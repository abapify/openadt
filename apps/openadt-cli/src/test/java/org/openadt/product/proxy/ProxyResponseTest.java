package org.openadt.product.proxy;

import org.openadt.sap.adt.sdk.ProxyResponse;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProxyResponseTest {
    @Test
    void testConstruction() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/xml");
        byte[] body = "<data/>".getBytes();

        ProxyResponse response = new ProxyResponse("HTTP/1.1", 200, "OK", headers, body);

        assertEquals("HTTP/1.1", response.version());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.reasonPhrase());
        assertEquals(1, response.headers().size());
        assertArrayEquals(body, response.body());
    }

    @Test
    void testBinaryBody() {
        byte[] binaryBody = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE};
        ProxyResponse response = new ProxyResponse("HTTP/1.1", 200, "OK", Map.of(), binaryBody);
        assertArrayEquals(binaryBody, response.body());
    }

    @Test
    void testHeaderAccess() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-CSRF-Token", "abc123");

        ProxyResponse response = new ProxyResponse("HTTP/1.1", 200, "OK", headers, new byte[0]);

        assertEquals("application/json", response.getHeader("content-type"));
        assertEquals("application/json", response.getHeader("Content-Type"));
        assertEquals("abc123", response.getHeader("X-CSRF-Token"));
        assertNull(response.getHeader("Authorization"));
    }

    @Test
    void testErrorResponse() {
        ProxyResponse response = new ProxyResponse("HTTP/1.1", 404, "Not Found", Map.of(), new byte[0]);
        assertEquals(404, response.statusCode());
        assertEquals("Not Found", response.reasonPhrase());
    }
}
