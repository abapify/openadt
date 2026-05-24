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

    public static final class ProxyEndpoint {
        private final String systemAlias;
        private final String profileName;
        private final String host;
        private final int port;
        private final boolean basicAuth;
        private final String username;

        public ProxyEndpoint(
            String systemAlias,
            String profileName,
            String host,
            int port,
            boolean basicAuth,
            String username
        ) {
            this.systemAlias = systemAlias;
            this.profileName = profileName;
            this.host = host;
            this.port = port;
            this.basicAuth = basicAuth;
            this.username = username;
        }

        public ProxyEndpoint(String systemAlias, String host, int port, boolean basicAuth, String username) {
            this(systemAlias, null, host, port, basicAuth, username);
        }

        public String getSystemAlias() {
            return systemAlias;
        }

        public String getProfileName() {
            return profileName;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public boolean isBasicAuth() {
            return basicAuth;
        }

        public String getUsername() {
            return username;
        }
    }

    public static Path registryDirectory() {
        return Path.of(System.getProperty("user.home"), ".openadt", "runtime");
    }

    public static Path registryFile(String systemAlias) {
        return registryFile(systemAlias, null);
    }

    public static Path registryFile(String systemAlias, String profileName) {
        return registryDirectory().resolve("proxy-" + registryKey(systemAlias, profileName) + ".json");
    }

    public static void register(ProxyEndpoint endpoint) throws IOException {
        Files.createDirectories(registryDirectory());
        MAPPER.writeValue(
            registryFile(endpoint.getSystemAlias(), endpoint.getProfileName()).toFile(),
            endpoint
        );
    }

    public static void unregister(String systemAlias) throws IOException {
        unregister(systemAlias, null);
    }

    public static void unregister(String systemAlias, String profileName) throws IOException {
        Files.deleteIfExists(registryFile(systemAlias, profileName));
    }

    public static Optional<ProxyEndpoint> read(String systemAlias) {
        return read(systemAlias, null);
    }

    public static Optional<ProxyEndpoint> read(String systemAlias, String profileName) {
        Path file = registryFile(systemAlias, profileName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            ProxyEndpoint endpoint = MAPPER.readValue(file.toFile(), ProxyEndpointRecord.class)
                .toEndpoint(systemAlias, profileName);
            return Optional.of(endpoint);
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    public static Optional<ProxyEndpoint> findActive(String systemAlias) {
        return findActive(systemAlias, null);
    }

    public static Optional<ProxyEndpoint> findActive(String systemAlias, String profileName) {
        Optional<ProxyEndpoint> endpoint = read(systemAlias, profileName).filter(LocalProxyRegistry::isAlive);
        if (endpoint.isPresent() || profileName == null || profileName.isBlank()) {
            return endpoint;
        }
        return read(systemAlias, null).filter(LocalProxyRegistry::isAlive);
    }

    public static boolean isAlive(ProxyEndpoint endpoint) {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getPort() <= 0) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.getHost(), endpoint.getPort()), 500);
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

    static String registryKey(String systemAlias, String profileName) {
        String key = sanitizeAlias(systemAlias);
        if (profileName != null && !profileName.isBlank()) {
            key += "-" + sanitizeAlias(profileName);
        }
        return key;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProxyEndpointRecord(
        String systemAlias,
        String profileName,
        String host,
        int port,
        boolean basicAuth,
        String username
    ) {
        ProxyEndpoint toEndpoint(String fallbackAlias, String fallbackProfile) {
            String alias = systemAlias != null && !systemAlias.isBlank() ? systemAlias : fallbackAlias;
            String profile = profileName != null && !profileName.isBlank() ? profileName : fallbackProfile;
            return new ProxyEndpoint(alias, profile, host, port, basicAuth, username);
        }
    }
}
