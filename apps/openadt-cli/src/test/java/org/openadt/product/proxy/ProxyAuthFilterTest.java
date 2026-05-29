package org.openadt.product.proxy;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProxyAuthFilterTest {
    @Test
    void testAllowValidBasicAuth() throws IOException {
        TestExchange exchange = new TestExchange();
        String credentials = Base64.getEncoder().encodeToString("user:pass".getBytes());
        exchange.getRequestHeaders().add("Authorization", "Basic " + credentials);
        RecordingHandler handler = new RecordingHandler();
        Filter.Chain chain = new Filter.Chain(List.of(), handler);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        assertSame(exchange, handler.exchange);
        assertEquals(1, handler.callCount);
    }

    @Test
    void testRejectMissingAuth() throws IOException {
        TestExchange exchange = new TestExchange();
        RecordingHandler handler = new RecordingHandler();
        Filter.Chain chain = new Filter.Chain(List.of(), handler);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        assertEquals(0, handler.callCount);
        assertEquals(401, exchange.responseCode);
    }

    @Test
    void testRejectWrongCredentials() throws IOException {
        TestExchange exchange = new TestExchange();
        String credentials = Base64.getEncoder().encodeToString("user:wrongpass".getBytes());
        exchange.getRequestHeaders().add("Authorization", "Basic " + credentials);
        RecordingHandler handler = new RecordingHandler();
        Filter.Chain chain = new Filter.Chain(List.of(), handler);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        assertEquals(0, handler.callCount);
        assertEquals(401, exchange.responseCode);
    }

    @Test
    void testRejectNonBasicAuth() throws IOException {
        TestExchange exchange = new TestExchange();
        exchange.getRequestHeaders().add("Authorization", "Bearer some-token");
        RecordingHandler handler = new RecordingHandler();
        Filter.Chain chain = new Filter.Chain(List.of(), handler);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        assertEquals(0, handler.callCount);
        assertEquals(401, exchange.responseCode);
    }

    private static class RecordingHandler implements HttpHandler {
        private int callCount;
        private HttpExchange exchange;

        @Override
        public void handle(HttpExchange exchange) {
            this.callCount++;
            this.exchange = exchange;
        }
    }

    private static class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode;

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/"); }
        @Override public String getRequestMethod() { return "GET"; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { /* test double noop */ }
        @Override public InputStream getRequestBody() { return InputStream.nullInputStream(); }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int responseCode, long responseLength) { this.responseCode = responseCode; }
        @Override public int getResponseCode() { return responseCode; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { /* test double noop */ }
        @Override public void setStreams(InputStream input, OutputStream output) { /* test double noop */ }
        @Override public com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null; }
    }
}
