package org.openadt.proxy;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyAuthFilterTest {
    @Mock
    private Filter.Chain chain;

    @Test
    void testAllowValidBasicAuth() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        String credentials = Base64.getEncoder().encodeToString("user:pass".getBytes());
        requestHeaders.add("Authorization", "Basic " + credentials);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        verify(chain).doFilter(exchange);
    }

    @Test
    void testRejectMissingAuth() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        verify(chain, never()).doFilter(any());
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void testRejectWrongCredentials() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        String credentials = Base64.getEncoder().encodeToString("user:wrongpass".getBytes());
        requestHeaders.add("Authorization", "Basic " + credentials);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        verify(chain, never()).doFilter(any());
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void testRejectNonBasicAuth() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        requestHeaders.add("Authorization", "Bearer some-token");
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(baos);

        ProxyAuthFilter filter = new ProxyAuthFilter("user", "pass");
        filter.doFilter(exchange, chain);

        verify(chain, never()).doFilter(any());
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }
}
