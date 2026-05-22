package org.openadt.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureLoginHubClientTest {
    private HttpServer server;
    private String baseUrl;
    private volatile String lastPath;
    private volatile String lastOrigin;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastOrigin = exchange.getRequestHeaders().getFirst("Origin");
            if (lastPath.endsWith("/status")) {
                writeJson(exchange, 200, "{\"profileid\":\"p1\",\"status\":\"LOGGED_OUT\"}");
                return;
            }
            if (lastPath.endsWith("/login")) {
                writeJson(exchange, 200, "{}");
                return;
            }
            exchange.sendResponseHeaders(403, 0);
            exchange.close();
        });
        server.setExecutor(null);
        server.start();
        int port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsWebAdapterStatusWithOriginHeader() throws Exception {
        OpenAdtConfig.SecureLoginConfig secureLogin = new OpenAdtConfig.SecureLoginConfig();
        secureLogin.setLocalSecurityHub(baseUrl);
        secureLogin.setOrigin("https://sls.example.com:50001");
        secureLogin.setReferer("https://sls.example.com:50001/");

        SecureLoginHubClient client = new SecureLoginHubClient(
            secureLogin.getLocalSecurityHub(),
            secureLogin.getOrigin(),
            secureLogin.getReferer(),
            plainHttpClient(),
            new com.fasterxml.jackson.databind.ObjectMapper()
        );

        assertEquals("LOGGED_OUT", client.webAdapterStatus("p1"));
        assertEquals("/slc3/api/status", lastPath);
        assertEquals("https://sls.example.com:50001", lastOrigin);
    }

    @Test
    void ensureLoggedInFailsWhenStatusStaysLoggedOut() {
        OpenAdtConfig.SecureLoginConfig secureLogin = new OpenAdtConfig.SecureLoginConfig();
        secureLogin.setLocalSecurityHub(baseUrl);
        secureLogin.setOrigin("https://sls.example.com:50001");
        secureLogin.setReferer("https://sls.example.com:50001/");

        SecureLoginHubClient client = new SecureLoginHubClient(
            secureLogin.getLocalSecurityHub(),
            secureLogin.getOrigin(),
            secureLogin.getReferer(),
            plainHttpClient(),
            new com.fasterxml.jackson.databind.ObjectMapper()
        );

        assertThrows(IllegalStateException.class, () -> client.ensureWebAdapterLoggedIn("p1"));
    }

    @Test
    void trustAllTlsIsLimitedToLoopbackHosts() {
        assertTrue(SecureLoginHubClient.isLoopbackHub("https://127.0.0.1:34443"));
        assertTrue(SecureLoginHubClient.isLoopbackHub("https://localhost:34443"));
        assertFalse(SecureLoginHubClient.isLoopbackHub("https://sap.example.com:34443"));
    }

    private static HttpClient plainHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
