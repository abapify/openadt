package org.openadt.product.proxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;
import org.openadt.config.SystemProfile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdtProxyHandlerTest {
    @Test
    void testStripsAuthHeaders() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/programs/programs", new byte[0]);
        Headers headers = new Headers();
        headers.add("Authorization", "Basic dXNlcjpwYXNz");
        headers.add("X-SAP-LogonToken", "secret-token");
        headers.add("X-SAP-Reentrance-Ticket", "reentrance-ticket");
        headers.add("SAP-SNC-Token", "snc-token");
        headers.add("Cookie", "MYSAPSSO2=abc");
        headers.add("Accept", "application/xml");
        exchange.requestHeaders.putAll(headers);

        AdtProxyHandler handler = newHandler();
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertNull(request.getHeader("Authorization"));
        assertNull(request.getHeader("X-SAP-LogonToken"));
        assertNull(request.getHeader("X-SAP-Reentrance-Ticket"));
        assertNull(request.getHeader("SAP-SNC-Token"));
        assertNull(request.getHeader("Cookie"));
        assertEquals("application/xml", request.getHeader("Accept"));
    }

    @Test
    void testPreservesAdtHeaders() throws IOException {
        TestExchange exchange = new TestExchange("PUT", "/sap/bc/adt/programs/programs/MY_PROG/source/main", "content".getBytes());
        Headers headers = new Headers();
        headers.add("Accept", "application/xml");
        headers.add("Content-Type", "application/xml");
        headers.add("X-CSRF-Token", "fetch");
        headers.add("If-Match", "\"abc123\"");
        exchange.requestHeaders.putAll(headers);

        AdtProxyHandler handler = newHandler();
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertEquals("application/xml", request.getHeader("Accept"));
        assertEquals("application/xml", request.getHeader("Content-Type"));
        assertEquals("fetch", request.getHeader("X-CSRF-Token"));
        assertEquals("\"abc123\"", request.getHeader("If-Match"));
    }

    @Test
    void testPathForwarding() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/programs/programs?$top=10", new byte[0]);

        AdtProxyHandler handler = newHandler();
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertEquals("/sap/bc/adt/programs/programs?$top=10", request.uri());
        assertEquals("GET", request.method());
    }

    @Test
    void testStripsSetCookieHeader() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/", new byte[0]);
        Headers headers = new Headers();
        headers.add("Set-Cookie", "JSESSIONID=abc123");
        headers.add("Accept", "application/json");
        exchange.requestHeaders.putAll(headers);

        AdtProxyHandler handler = newHandler();
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertNull(request.getHeader("Set-Cookie"));
        assertEquals("application/json", request.getHeader("Accept"));
    }

    @Test
    void testDoesNotForwardResponseCookies() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/", new byte[0]);
        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse(
                "HTTP/1.1",
                200,
                "OK",
                Map.of(
                    "Set-Cookie", "JSESSIONID=abc123",
                    "Set-Cookie2", "Legacy=1",
                    "Content-Type", "application/json"
                ),
                "{}".getBytes()
            )
        );

        handler.handle(exchange);

        assertNull(exchange.getResponseHeaders().getFirst("Set-Cookie"));
        assertNull(exchange.getResponseHeaders().getFirst("Set-Cookie2"));
        assertEquals("application/json", exchange.getResponseHeaders().getFirst("Content-Type"));
    }

    private static AdtProxyHandler newHandler() {
        return sdkHandler((system, request) -> new ProxyResponse("HTTP/1.1", 200, "OK", Map.of(), new byte[0]));
    }

    /**
     * Wraps a responder as an SDK-like transport — one that manages CSRF upstream, so the local
     * handshake applies. A bare lambda defaults to {@code managesCsrfUpstream() == false}.
     */
    private static AdtProxyHandler sdkHandler(AdtTransportClient responder) {
        return new AdtProxyHandler(new SystemProfile(), new AdtTransportClient() {
            @Override
            public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
                return responder.execute(system, request);
            }

            @Override
            public boolean managesCsrfUpstream() {
                return true;
            }
        });
    }

    // --- CSRF handshake -----------------------------------------------------

    @Test
    void headCsrfFetchIsAnsweredLocallyWithAToken() throws IOException {
        TestExchange exchange = new TestExchange("HEAD", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        // Upstream rejects HEAD with 400, so the handshake must not reach it at all.
        AdtProxyHandler handler = sdkHandler((system, request) -> {
            throw new AssertionError("upstream must not be called for a HEAD CSRF fetch");
        });
        handler.handle(exchange);

        assertEquals(200, exchange.sentCode);
        String token = exchange.getResponseHeaders().getFirst("X-CSRF-Token");
        assertNotNull(token);
        assertFalse(token.isBlank());
        // Clients treat the literal "Required" as "no token issued".
        assertNotEquals("Required", token);
    }

    @Test
    void headCsrfFetchSendsNoBody() throws IOException {
        TestExchange exchange = new TestExchange("HEAD", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        newHandler().handle(exchange);

        // com.sun.net.httpserver requires -1 for a bodyless reply.
        assertEquals(-1L, exchange.sentLength);
        assertEquals(0, exchange.responseBody.size());
    }

    @Test
    void csrfFetchIsCaseInsensitiveAndToleratesWhitespace() throws IOException {
        TestExchange exchange = new TestExchange("HEAD", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("x-csrf-token", " Fetch ");

        newHandler().handle(exchange);

        assertEquals(200, exchange.sentCode);
        assertNotNull(exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    @Test
    void getCsrfFetchKeepsItsBodyAndGainsAToken() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        AdtProxyHandler handler = sdkHandler((system, request) -> new ProxyResponse(
            "HTTP/1.1", 200, "OK", Map.of("Content-Type", "application/xml"), "<discovery/>".getBytes()
        ));
        handler.handle(exchange);

        assertEquals(200, exchange.sentCode);
        assertEquals("<discovery/>", exchange.responseBody.toString());
        assertNotNull(exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    @Test
    void upstreamCsrfTokenIsNotOverwritten() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        AdtProxyHandler handler = sdkHandler((system, request) -> new ProxyResponse(
            "HTTP/1.1", 200, "OK", Map.of("X-CSRF-Token", "real-upstream-token"), new byte[0]
        ));
        handler.handle(exchange);

        assertEquals("real-upstream-token", exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    @Test
    void ordinaryRequestGetsNoSyntheticToken() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/discovery", new byte[0]);

        newHandler().handle(exchange);

        assertNull(exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    @Test
    void upstreamRequiredSentinelIsReplacedWithAUsableToken() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        // SAP answers "Required" to mean "fetch a token"; relaying it fails the handshake as surely
        // as sending no header at all.
        AdtProxyHandler handler = sdkHandler((system, request) -> new ProxyResponse(
            "HTTP/1.1", 200, "OK", Map.of("X-CSRF-Token", "Required"), new byte[0]
        ));
        handler.handle(exchange);

        String token = exchange.getResponseHeaders().getFirst("X-CSRF-Token");
        assertNotEquals("Required", token);
        assertTrue(AdtProxyHandler.isUsableToken(token));
    }

    @Test
    void upstreamBlankTokenIsReplacedWithAUsableToken() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        AdtProxyHandler handler = sdkHandler((system, request) -> new ProxyResponse(
            "HTTP/1.1", 200, "OK", Map.of("X-CSRF-Token", "   "), new byte[0]
        ));
        handler.handle(exchange);

        assertTrue(AdtProxyHandler.isUsableToken(exchange.getResponseHeaders().getFirst("X-CSRF-Token")));
    }

    @Test
    void writeCarryingFetchGetsNoSyntheticToken() throws IOException {
        // The handshake is defined for GET and HEAD. A POST tagged `fetch` is not a handshake, so its
        // response must not gain an invented token.
        TestExchange exchange = new TestExchange("POST", "/sap/bc/adt/programs/programs", "body".getBytes());
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        sdkHandler((system, request) -> new ProxyResponse("HTTP/1.1", 201, "Created", Map.of(), new byte[0]))
            .handle(exchange);

        assertEquals(201, exchange.sentCode);
        assertNull(exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    @Test
    void headHandshakeIsForwardedForTransportsThatDoNotManageCsrf() throws IOException {
        TestExchange exchange = new TestExchange("HEAD", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        // rest-rfc and http forward the client's token to SAP, so a locally minted one would be
        // rejected on the first write. Their behaviour must stay exactly as it was: pass it through.
        boolean[] reachedUpstream = {false};
        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> {
                reachedUpstream[0] = true;
                return new ProxyResponse("HTTP/1.1", 400, "Bad Request", Map.of(), new byte[0]);
            }
        );
        handler.handle(exchange);

        assertTrue(reachedUpstream[0], "a non-CSRF-managing transport must still see the request");
        assertEquals(400, exchange.sentCode);
        assertNull(exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    @Test
    void getHandshakeGainsNoTokenForTransportsThatDoNotManageCsrf() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/core/discovery", new byte[0]);
        exchange.requestHeaders.add("X-CSRF-Token", "fetch");

        new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse("HTTP/1.1", 200, "OK", Map.of(), "<x/>".getBytes())
        ).handle(exchange);

        assertEquals("<x/>", exchange.responseBody.toString());
        assertNull(exchange.getResponseHeaders().getFirst("X-CSRF-Token"));
    }

    // --- bodyless response framing -----------------------------------------

    @Test
    void headWithoutCsrfFetchStillSendsNoBody() throws IOException {
        TestExchange exchange = new TestExchange("HEAD", "/sap/bc/adt/discovery", new byte[0]);

        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse(
                "HTTP/1.1", 200, "OK", Map.of(), "body-that-must-be-dropped".getBytes()
            )
        );
        handler.handle(exchange);

        assertEquals(200, exchange.sentCode);
        assertEquals(-1L, exchange.sentLength);
        assertEquals(0, exchange.responseBody.size());
    }

    @Test
    void noContentStatusSendsNoBody() throws IOException {
        TestExchange exchange = new TestExchange("DELETE", "/sap/bc/adt/programs/programs/P", new byte[0]);

        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse("HTTP/1.1", 204, "No Content", Map.of(), new byte[0])
        );
        handler.handle(exchange);

        assertEquals(204, exchange.sentCode);
        assertEquals(-1L, exchange.sentLength);
    }

    @Test
    void notModifiedStatusSendsNoBody() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/programs/programs/P", new byte[0]);

        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse("HTTP/1.1", 304, "Not Modified", Map.of(), new byte[0])
        );
        handler.handle(exchange);

        assertEquals(304, exchange.sentCode);
        assertEquals(-1L, exchange.sentLength);
    }

    @Test
    void responseWithBodyKeepsItsContentLength() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/discovery", new byte[0]);

        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse("HTTP/1.1", 200, "OK", Map.of(), "12345".getBytes())
        );
        handler.handle(exchange);

        assertEquals(5L, exchange.sentLength);
        assertEquals("12345", exchange.responseBody.toString());
    }

    @Test
    void upstreamFailureBecomesFiveHundredWithMessage() throws IOException {
        TestExchange exchange = new TestExchange("GET", "/sap/bc/adt/discovery", new byte[0]);

        AdtProxyHandler handler = new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> {
                throw new IllegalStateException("logon exploded");
            }
        );
        handler.handle(exchange);

        assertEquals(500, exchange.sentCode);
        assertEquals("logon exploded", exchange.responseBody.toString());
    }

    private static class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final String method;
        private final URI uri;
        private final byte[] requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        private TestExchange(String method, String uri, byte[] requestBody) {
            this.method = method;
            this.uri = URI.create(uri);
            this.requestBody = requestBody;
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return uri; }
        @Override public String getRequestMethod() { return method; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { /* test double noop */ }
        @Override public InputStream getRequestBody() { return new ByteArrayInputStream(requestBody); }
        @Override public OutputStream getResponseBody() { return responseBody; }

        private int sentCode = -1;
        private long sentLength = Long.MIN_VALUE;

        @Override public void sendResponseHeaders(int responseCode, long responseLength) {
            this.sentCode = responseCode;
            this.sentLength = responseLength;
        }

        @Override public int getResponseCode() { return sentCode; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { /* test double noop */ }
        @Override public void setStreams(InputStream input, OutputStream output) { /* test double noop */ }
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
