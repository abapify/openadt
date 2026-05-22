package org.openadt.proxy;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ProxyAuthFilter extends Filter {
    private final String expectedUsername;
    private final String expectedPassword;

    public ProxyAuthFilter(String expectedUsername, String expectedPassword) {
        this.expectedUsername = expectedUsername;
        this.expectedPassword = expectedPassword;
    }

    @Override
    public String description() {
        return "Basic authentication filter";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            sendUnauthorized(exchange);
            return;
        }

        String encoded = authHeader.substring("Basic ".length());
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            sendUnauthorized(exchange);
            return;
        }

        int colon = decoded.indexOf(':');
        if (colon < 0) {
            sendUnauthorized(exchange);
            return;
        }

        String username = decoded.substring(0, colon);
        String password = decoded.substring(colon + 1);

        if (!expectedUsername.equals(username) || !expectedPassword.equals(password)) {
            sendUnauthorized(exchange);
            return;
        }

        chain.doFilter(exchange);
    }

    private void sendUnauthorized(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"OpenADT\"");
        byte[] body = "Unauthorized".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(401, body.length);
        try (var os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
