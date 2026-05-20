package org.openadt.proxy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.openadt.core.AdtRestRfcClient;
import org.openadt.core.ProxyRequest;
import org.openadt.core.ProxyResponse;
import org.openadt.core.SystemProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class AdtProxyHandler implements HttpHandler {
    private static final Set<String> STRIPPED_HEADERS = Set.of(
        "authorization", "x-sap-logontoken", "x-sap-reentrance-ticket",
        "sap-snc-token", "cookie", "set-cookie"
    );

    private final SystemProfile systemProfile;
    private final AdtRestRfcClient rfcClient;

    public AdtProxyHandler(SystemProfile systemProfile, AdtRestRfcClient rfcClient) {
        this.systemProfile = systemProfile;
        this.rfcClient = rfcClient;
    }

    public ProxyRequest buildProxyRequest(HttpExchange exchange) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!STRIPPED_HEADERS.contains(key.toLowerCase())) {
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

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            ProxyRequest request = buildProxyRequest(exchange);
            ProxyResponse response = rfcClient.execute(systemProfile, request);

            response.headers().forEach((key, value) ->
                exchange.getResponseHeaders().add(key, value));

            byte[] body = response.body() != null ? response.body() : new byte[0];
            exchange.sendResponseHeaders(response.statusCode(), body.length);
            try (var os = exchange.getResponseBody()) {
                os.write(body);
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Internal Server Error";
            byte[] errorBody = msg.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, errorBody.length);
            try (var os = exchange.getResponseBody()) {
                os.write(errorBody);
            }
        }
    }
}
