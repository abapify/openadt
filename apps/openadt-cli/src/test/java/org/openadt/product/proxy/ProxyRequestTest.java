package org.openadt.product.proxy;

import org.openadt.sap.adt.sdk.ProxyRequest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProxyRequestTest {
    @Test
    void testConstruction() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/xml");
        headers.put("Content-Type", "text/plain");
        byte[] body = "hello".getBytes();

        ProxyRequest request = new ProxyRequest("GET", "/sap/bc/adt/test", "HTTP/1.1", headers, body);

        assertEquals("GET", request.method());
        assertEquals("/sap/bc/adt/test", request.uri());
        assertEquals("HTTP/1.1", request.version());
        assertEquals(2, request.headers().size());
        assertArrayEquals(body, request.body());
    }

    @Test
    void testHeaderAccess() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/xml");
        headers.put("X-CSRF-Token", "fetch");

        ProxyRequest request = new ProxyRequest("GET", "/test", "HTTP/1.1", headers, new byte[0]);

        assertEquals("application/xml", request.getHeader("Accept"));
        assertEquals("application/xml", request.getHeader("accept"));
        assertEquals("application/xml", request.getHeader("ACCEPT"));
        assertEquals("fetch", request.getHeader("x-csrf-token"));
        assertNull(request.getHeader("Authorization"));
    }

    @Test
    void testEmptyBody() {
        ProxyRequest request = new ProxyRequest("GET", "/test", "HTTP/1.1", Map.of(), new byte[0]);
        assertNotNull(request.body());
        assertEquals(0, request.body().length);
    }
}
