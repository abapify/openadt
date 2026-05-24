package org.openadt.proxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.openadt.core.ProxyRequest;
import org.openadt.core.ProxyResponse;
import org.openadt.core.SystemProfile;

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
        return new AdtProxyHandler(
            new SystemProfile(),
            (system, request) -> new ProxyResponse("HTTP/1.1", 200, "OK", Map.of(), new byte[0])
        );
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
        @Override public void sendResponseHeaders(int responseCode, long responseLength) { /* test double noop */ }
        @Override public int getResponseCode() { return 0; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { /* test double noop */ }
        @Override public void setStreams(InputStream input, OutputStream output) { /* test double noop */ }
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
