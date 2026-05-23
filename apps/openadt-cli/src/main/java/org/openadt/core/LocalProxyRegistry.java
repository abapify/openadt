package org.openadt.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Tracks running {@code openadt proxy} instances so {@code fetch} can reuse a warm SDK session.
 */
public final class LocalProxyRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LocalProxyRegistry() {
    }

    public record ProxyEndpoint(String systemAlias, String host, int port, boolean basicAuth, String username) {
    }

    public static Path registryDirectory() {
        return Path.of(System.getProperty("user.home"), ".openadt", "runtime");
    }

    public static Path registryFile(String systemAlias) {
        return registryDirectory().resolve("proxy-" + sanitizeAlias(systemAlias) + ".json");
    }

    public static void register(ProxyEndpoint endpoint) throws IOException {
        Files.createDirectories(registryDirectory());
        MAPPER.writeValue(registryFile(endpoint.systemAlias()).toFile(), endpoint);
    }

    public static void unregister(String systemAlias) throws IOException {
        Files.deleteIfExists(registryFile(systemAlias));
    }

    public static Optional<ProxyEndpoint> read(String systemAlias) {
        Path file = registryFile(systemAlias);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            ProxyEndpoint endpoint = MAPPER.readValue(file.toFile(), ProxyEndpointRecord.class).toEndpoint(systemAlias);
            return Optional.of(endpoint);
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    public static Optional<ProxyEndpoint> findActive(String systemAlias) {
        return read(systemAlias).filter(LocalProxyRegistry::isAlive);
    }

    public static boolean isAlive(ProxyEndpoint endpoint) {
        if (endpoint == null || endpoint.host() == null || endpoint.port() <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), 500);
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    static String sanitizeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return "default";
        }
        return alias.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ProxyEndpointRecord {
        public String systemAlias;
        public String host;
        public int port;
        public boolean basicAuth;
        public String username;

        ProxyEndpoint toEndpoint(String fallbackAlias) {
            String alias = systemAlias != null && !systemAlias.isBlank() ? systemAlias : fallbackAlias;
            return new ProxyEndpoint(alias, host, port, basicAuth, username);
        }
    }
}
