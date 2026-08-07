package org.openadt.product.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;
import org.openadt.config.SystemProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AdtProxyHandler implements HttpHandler {
    private static final Set<String> STRIPPED_REQUEST_HEADERS = Set.of(
        "authorization", "x-sap-logontoken", "x-sap-reentrance-ticket",
        "sap-snc-token", "cookie", "set-cookie"
    );
    private static final Set<String> STRIPPED_RESPONSE_HEADERS = Set.of("set-cookie", "set-cookie2");

    static final String CSRF_HEADER = "X-CSRF-Token";
    static final String CSRF_FETCH = "fetch";
    /** SAP's "you must fetch a token" sentinel; clients read it as no token at all. */
    static final String CSRF_REQUIRED = "Required";
    /**
     * Token handed back for a local CSRF handshake. Must be non-empty and must not be the literal
     * {@code Required}, which clients read as "no token issued".
     */
    static final String LOCAL_CSRF_TOKEN = "openadt-local-proxy";

    private final SystemProfile systemProfile;
    private final AdtTransportClient transportClient;

    public AdtProxyHandler(SystemProfile systemProfile, AdtTransportClient transportClient) {
        this.systemProfile = systemProfile;
        this.transportClient = transportClient;
    }

    public ProxyRequest buildProxyRequest(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!STRIPPED_REQUEST_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                headers.put(key, values.isEmpty() ? "" : values.get(0));
            }
        });

        byte[] body = exchange.getRequestBody().readAllBytes();
        return new ProxyRequest(
            exchange.getRequestMethod(),
            exchange.getRequestURI().toString(),
            "HTTP/1.1",
            headers,
            body
        );
    }

    private static boolean isValidResponseHeaderName(String name) {
        if (name == null || name.isBlank() || name.charAt(0) == '~') {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch <= 0x20 || ch >= 0x7f || ch == '(' || ch == ')' || ch == '<' || ch == '>'
                || ch == '@' || ch == ',' || ch == ';' || ch == ':' || ch == '\\' || ch == '"'
                || ch == '/' || ch == '[' || ch == ']' || ch == '?' || ch == '=' || ch == '{'
                || ch == '}') {
                return false;
            }
        }
        return true;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Only a transport that owns CSRF upstream may have the handshake served locally; for
            // rest-rfc and http the client's token does reach SAP, so a local one would be rejected.
            boolean localHandshake = isCsrfFetch(exchange) && transportClient.managesCsrfUpstream();

            // A CSRF fetch by HEAD is answered locally: upstream rejects HEAD with 400, and the
            // token is not needed upstream anyway (see answerCsrfFetch).
            if (localHandshake && isHead(exchange)) {
                answerCsrfFetch(exchange);
                return;
            }

            ProxyRequest request = buildProxyRequest(exchange);
            ProxyResponse response = transportClient.execute(systemProfile, request);

            response.headers().forEach((key, value) -> {
                if (isValidResponseHeaderName(key)
                    && !STRIPPED_RESPONSE_HEADERS.contains(key.toLowerCase(Locale.ROOT))) {
                    exchange.getResponseHeaders().add(key, value);
                }
            });

            // A CSRF fetch by GET keeps its body; only make sure it comes back with a usable token.
            // Restricted to GET because the handshake is defined for GET and HEAD only — a write
            // carrying `fetch` must not have a token invented for its response.
            if (localHandshake && isGet(exchange) && !isUsableToken(response.getHeader(CSRF_HEADER))) {
                exchange.getResponseHeaders().set(CSRF_HEADER, LOCAL_CSRF_TOKEN);
            }

            byte[] body = response.body() != null ? response.body() : new byte[0];
            writeResponse(exchange, response.statusCode(), body);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Internal Server Error";
            writeResponse(exchange, 500, msg.getBytes(StandardCharsets.UTF_8));
        }
    }

    static boolean isHead(HttpExchange exchange) {
        return "HEAD".equalsIgnoreCase(exchange.getRequestMethod());
    }

    static boolean isGet(HttpExchange exchange) {
        return "GET".equalsIgnoreCase(exchange.getRequestMethod());
    }

    /**
     * A client treats an absent, empty, or literal {@code Required} {@code x-csrf-token} as "no token
     * issued" and aborts before its write. SAP answers with {@code Required} when it wants a token
     * fetched, so relaying that value verbatim fails the handshake as surely as sending nothing.
     */
    static boolean isUsableToken(String value) {
        return value != null && !value.isBlank() && !CSRF_REQUIRED.equalsIgnoreCase(value.trim());
    }

    /** SAP's CSRF handshake: any GET/HEAD carrying {@code X-CSRF-Token: fetch}. */
    static boolean isCsrfFetch(HttpExchange exchange) {
        String value = exchange.getRequestHeaders().getFirst(CSRF_HEADER);
        return value != null && CSRF_FETCH.equalsIgnoreCase(value.trim());
    }

    /**
     * Completes a CSRF handshake without contacting SAP.
     *
     * <p>The ADT SDK keeps its own authenticated session and manages CSRF upstream, so a token
     * minted here is never checked against anything — a write carrying an arbitrary token already
     * succeeds. The handshake only has to satisfy the client, which requires a non-empty
     * {@code x-csrf-token} that is not the literal {@code Required}.
     *
     * <p>Answering locally also side-steps upstream returning 400 for HEAD, which otherwise breaks
     * every client that fetches its token that way before issuing writes.
     */
    private void answerCsrfFetch(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set(CSRF_HEADER, LOCAL_CSRF_TOKEN);
        writeResponse(exchange, 200, new byte[0]);
    }

    /**
     * {@code com.sun.net.httpserver} demands {@code sendResponseHeaders(status, -1)} for a response
     * that carries no body — a HEAD reply, 204, 304, or simply an empty body. Passing a length there
     * (including {@code 0}, which selects chunked encoding) makes it emit a framing it then cannot
     * satisfy.
     */
    private static void writeResponse(HttpExchange exchange, int statusCode, byte[] body)
        throws IOException {
        if (isHead(exchange) || statusCode == 204 || statusCode == 304 || body.length == 0) {
            exchange.sendResponseHeaders(statusCode, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(statusCode, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
