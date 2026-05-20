package org.openadt.proxy;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openadt.core.AdtRestRfcClient;
import org.openadt.core.ProxyRequest;
import org.openadt.core.SystemProfile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdtProxyHandlerTest {
    @Mock
    private SystemProfile systemProfile;

    @Mock
    private AdtRestRfcClient rfcClient;

    @Test
    void testStripsAuthHeaders() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        headers.add("Authorization", "Basic dXNlcjpwYXNz");
        headers.add("X-SAP-LogonToken", "secret-token");
        headers.add("X-SAP-Reentrance-Ticket", "reentrance-ticket");
        headers.add("SAP-SNC-Token", "snc-token");
        headers.add("Cookie", "MYSAPSSO2=abc");
        headers.add("Accept", "application/xml");
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/sap/bc/adt/programs/programs"));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        AdtProxyHandler handler = new AdtProxyHandler(systemProfile, rfcClient);
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
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        headers.add("Accept", "application/xml");
        headers.add("Content-Type", "application/xml");
        headers.add("X-CSRF-Token", "fetch");
        headers.add("If-Match", "\"abc123\"");
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRequestMethod()).thenReturn("PUT");
        when(exchange.getRequestURI()).thenReturn(URI.create("/sap/bc/adt/programs/programs/MY_PROG/source/main"));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream("content".getBytes()));

        AdtProxyHandler handler = new AdtProxyHandler(systemProfile, rfcClient);
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertEquals("application/xml", request.getHeader("Accept"));
        assertEquals("application/xml", request.getHeader("Content-Type"));
        assertEquals("fetch", request.getHeader("X-CSRF-Token"));
        assertEquals("\"abc123\"", request.getHeader("If-Match"));
    }

    @Test
    void testPathForwarding() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/sap/bc/adt/programs/programs?$top=10"));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        AdtProxyHandler handler = new AdtProxyHandler(systemProfile, rfcClient);
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertEquals("/sap/bc/adt/programs/programs?$top=10", request.uri());
        assertEquals("GET", request.method());
    }

    @Test
    void testStripsSetCookieHeader() throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        headers.add("Set-Cookie", "JSESSIONID=abc123");
        headers.add("Accept", "application/json");
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getRequestURI()).thenReturn(URI.create("/sap/bc/adt/"));
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[0]));

        AdtProxyHandler handler = new AdtProxyHandler(systemProfile, rfcClient);
        ProxyRequest request = handler.buildProxyRequest(exchange);

        assertNull(request.getHeader("Set-Cookie"));
        assertEquals("application/json", request.getHeader("Accept"));
    }
}
