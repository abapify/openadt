package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSapCookieStoreTest {
    @Test
    void parsesSetCookieHeaders() {
        HttpHeaders headers = HttpHeaders.of(
            Map.of("Set-Cookie", List.of("SAP_SESSIONID=abc123; path=/; HttpOnly", "sap-contextcookie=X")),
            (a, b) -> true
        );
        Map<String, String> cookies = HttpSapCookieStore.fromSetCookieHeaders(headers);
        assertEquals("abc123", cookies.get("SAP_SESSIONID"));
        assertEquals("X", cookies.get("sap-contextcookie"));
    }

    @Test
    void buildsCookieHeaderWithSessionCookiesAndClient() {
        Map<String, String> session = Map.of("SAP_SESSIONID", "abc");
        String header = HttpSapCookieStore.buildCookieHeader("ticket", "200", session);
        assertTrue(header.contains("MYSAPSSO2=ticket"));
        assertTrue(header.contains("SAP_SESSIONID=abc"));
        assertTrue(header.contains("sap-usercontext=sap-client=200"));
    }
}
