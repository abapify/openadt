package org.openadt.sap.adt.fallback.http;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.openadt.config.AdtHttpFrontendUrls;
import org.openadt.config.CliLog;
import org.openadt.config.SystemProfile;
/**
 * Persists HTTP SSO sessions (MYSAPSSO2 + resolved ADT API base) under {@code ~/.openadt/cache/http-sso/}.
 */
public final class HttpSsoTicketCache {
    static final String DISABLE_ENV = "OPENADT_HTTP_SSO_NO_CACHE";
    private static final String CACHE_SUBDIR = "cache/http-sso";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path cacheRoot;
    private final UnaryOperator<String> envProvider;
    private final boolean requestNoCache;
    private final Clock clock;

    public HttpSsoTicketCache() {
        this(resolveUserOpenAdtHome(), System::getenv, false);
    }

    HttpSsoTicketCache(Path openadtHome, UnaryOperator<String> envProvider) {
        this(openadtHome, envProvider, false);
    }

    HttpSsoTicketCache(Path openadtHome, UnaryOperator<String> envProvider, boolean requestNoCache) {
        this(openadtHome, envProvider, requestNoCache, Clock.systemUTC());
    }

    HttpSsoTicketCache(Path openadtHome, UnaryOperator<String> envProvider, boolean requestNoCache, Clock clock) {
        this.cacheRoot = openadtHome.resolve(CACHE_SUBDIR);
        this.envProvider = envProvider;
        this.requestNoCache = requestNoCache;
        this.clock = clock;
    }

    public record CachedSession(String ticket, String apiBase, Map<String, String> cookies) {
        public CachedSession(String ticket, String apiBase) {
            this(ticket, apiBase, Map.of());
        }

        public boolean hasTicket() {
            return ticket != null && !ticket.isBlank();
        }

        public boolean hasApiBase() {
            return apiBase != null && !apiBase.isBlank();
        }

        public Map<String, String> cookiesOrEmpty() {
            return cookies != null ? cookies : Map.of();
        }
    }

    public Optional<CachedSession> readSession(SystemProfile system) {
        if (cacheDisabled()) {
            logCacheDisabled();
            return Optional.empty();
        }
        Path file = cacheFile(system);
        if (!Files.isRegularFile(file)) {
            logCacheKeyMaterial(system);
            return Optional.empty();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            CachedSession session = parseSession(raw);
            if (!session.hasTicket()) {
                return Optional.empty();
            }
            if (SapLogonTicketValidity.isExpired(session.ticket(), clock)) {
                CliLog.httpSso("disk cache ticket past validity; invalidating");
                invalidate(system);
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (IOException error) {
            CliLog.httpSso("cache read failed for " + file + ": " + error.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> read(SystemProfile system) {
        return readSession(system).map(CachedSession::ticket);
    }

    public void write(SystemProfile system, String ticket) {
        CachedSession existing = readSession(system).orElse(null);
        String apiBase = existing != null ? existing.apiBase() : null;
        Map<String, String> cookies = existing != null ? existing.cookiesOrEmpty() : Map.of();
        writeSession(system, new CachedSession(ticket, apiBase, cookies));
    }

    public void writeApiBase(SystemProfile system, String apiBase) {
        CachedSession existing = readSession(system).orElse(null);
        if (existing == null || !existing.hasTicket()) {
            return;
        }
        writeSession(system, new CachedSession(existing.ticket(), apiBase, existing.cookiesOrEmpty()));
    }

    public void mergeCookies(SystemProfile system, Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return;
        }
        CachedSession existing = readSession(system).orElse(null);
        if (existing == null || !existing.hasTicket()) {
            return;
        }
        Map<String, String> merged = HttpSapCookieStore.copyOf(existing.cookiesOrEmpty());
        HttpSapCookieStore.merge(merged, incoming);
        writeSession(system, new CachedSession(existing.ticket(), existing.apiBase(), merged));
    }

    public void writeSession(SystemProfile system, CachedSession session) {
        if (cacheDisabled()) {
            logCacheDisabledWrite();
            return;
        }
        if (session == null || !session.hasTicket()) {
            CliLog.httpSso("disk cache skip: empty session");
            return;
        }
        Path file = cacheFile(system);
        try {
            ensureCacheDirectory();
            Path temp = createOwnerOnlyTempFile(file.getParent(), file.getFileName().toString());
            try {
                Files.writeString(temp, serializeSession(session), StandardCharsets.UTF_8);
                restrictToOwner(temp);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
            logCacheKeyMaterial(system);
        } catch (IOException error) {
            CliLog.httpSso("cache write failed for " + file + ": " + error.getMessage());
        }
    }

    private void ensureCacheDirectory() throws IOException {
        if (!Files.isDirectory(cacheRoot)) {
            Files.createDirectories(cacheRoot);
        }
        restrictCacheDirectory(cacheRoot);
    }

    public void invalidate(SystemProfile system) {
        if (cacheDisabled()) {
            return;
        }
        Path file = cacheFile(system);
        try {
            if (Files.deleteIfExists(file)) {
                CliLog.httpSso("cache invalidated: " + file);
            }
        } catch (IOException error) {
            CliLog.httpSso("cache delete failed for " + file + ": " + error.getMessage());
        }
    }

    Path cacheDirectory() {
        return cacheRoot;
    }

    Path cacheFile(SystemProfile system) {
        return cacheRoot.resolve(cacheKey(system));
    }

    static String cacheKey(SystemProfile system) {
        return sha256Hex(cacheKeyMaterial(system)) + ".ticket";
    }

    static String cacheKeyMaterial(SystemProfile system) {
        String alias = system != null && system.getAlias() != null ? system.getAlias().trim() : "unknown";
        String profile = system != null && system.getActiveProfile() != null ? system.getActiveProfile().trim() : "";
        String client = system != null && system.getClient() != null ? system.getClient().trim() : "";
        String frontend = "";
        if (system != null && system.getAdt() != null) {
            String origin = AdtHttpFrontendUrls.resolveFrontendOrigin(system.getAdt());
            if (origin != null) {
                frontend = normalizeDiscoveryUrl(origin);
            }
        }
        return alias + "\0" + profile + "\0" + client + "\0" + frontend;
    }

    static String normalizeDiscoveryUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = AdtHttpPaths.withHttpsSchemeIfMissing(raw.trim());
        URI uri = parseDiscoveryUri(value);
        if (uri == null) {
            return value;
        }
        return formatNormalizedDiscoveryUri(uri);
    }

    private static URI parseDiscoveryUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String formatNormalizedDiscoveryUri(URI uri) {
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "https";
        String authority = normalizedAuthority(uri, scheme);
        String path = normalizedDiscoveryPath(uri.getPath());
        return scheme + "://" + authority + path;
    }

    private static String normalizedAuthority(URI uri, String scheme) {
        String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
        int port = uri.getPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
            || ("https".equals(scheme) && port == 443);
        return port > 0 && !defaultPort ? host + ":" + port : host;
    }

    private static String normalizedDiscoveryPath(String rawPath) {
        String path = rawPath != null ? rawPath : "";
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isBlank() ? "/" : path;
    }

    private static CachedSession parseSession(String raw) throws IOException {
        if (raw.startsWith("{")) {
            SessionFileDto dto = MAPPER.readValue(raw, SessionFileDto.class);
            return new CachedSession(
                blankToNull(dto.ticket),
                blankToNull(dto.apiBase),
                dto.cookies != null ? new LinkedHashMap<>(dto.cookies) : Map.of()
            );
        }
        String ticket = raw;
        if (ticket.startsWith("MYSAPSSO2=")) {
            ticket = ticket.substring("MYSAPSSO2=".length()).trim();
        }
        return new CachedSession(ticket, null, Map.of());
    }

    private static String serializeSession(CachedSession session) throws IOException {
        SessionFileDto dto = new SessionFileDto();
        dto.ticket = session.ticket();
        dto.apiBase = session.apiBase();
        dto.cookies = session.cookiesOrEmpty().isEmpty() ? null : new LinkedHashMap<>(session.cookiesOrEmpty());
        return MAPPER.writeValueAsString(dto);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void logCacheKeyMaterial(SystemProfile system) {
        if (!CliLog.verbose()) {
            return;
        }
        CliLog.httpSso("cache key material: " + cacheKeyMaterial(system));
        CliLog.httpSso("cache key file: " + cacheKey(system));
    }

    private boolean cacheDisabled() {
        if (requestNoCache) {
            return true;
        }
        return envCacheDisabled();
    }

    static boolean envCacheDisabled(UnaryOperator<String> envProvider) {
        String value = envProvider.apply(DISABLE_ENV);
        return value != null && !value.isBlank()
            && !"0".equals(value.trim())
            && !"false".equalsIgnoreCase(value.trim());
    }

    private boolean envCacheDisabled() {
        return envCacheDisabled(envProvider);
    }

    private void logCacheDisabled() {
        if (requestNoCache) {
            CliLog.httpSso("disk cache disabled for this fetch (--no-cache)");
        } else {
            CliLog.httpSso("disk cache disabled (" + DISABLE_ENV + " is set)");
        }
    }

    private void logCacheDisabledWrite() {
        if (requestNoCache) {
            CliLog.httpSso("disk cache disabled for this fetch; session not stored (--no-cache)");
        } else {
            CliLog.httpSso("disk cache disabled; session not stored");
        }
    }

    static Path resolveUserOpenAdtHome() {
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("user.home is not set; cannot store HTTP SSO ticket cache");
        }
        return Path.of(home).resolve(".openadt");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 not available", error);
        }
    }

    private static Path createOwnerOnlyTempFile(Path dir, String prefix) throws IOException {
        try {
            return Files.createTempFile(
                dir,
                prefix + ".",
                ".tmp",
                PosixFilePermissions.asFileAttribute(Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                ))
            );
        } catch (UnsupportedOperationException ignored) {
            return Files.createTempFile(dir, prefix + ".", ".tmp");
        }
    }

    private static void restrictCacheDirectory(Path directory) {
        try {
            Files.setPosixFilePermissions(
                directory,
                Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE
                )
            );
        } catch (UnsupportedOperationException | IOException ignored) {
            if (directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                CliLog.httpSso("could not restrict cache directory permissions on " + directory);
            }
        }
    }

    private static void restrictToOwner(Path file) {
        try {
            Set<PosixFilePermission> ownerOnly = Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(file, ownerOnly);
        } catch (UnsupportedOperationException | IOException ignored) {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                CliLog.httpSso("could not restrict cache permissions on " + file);
            }
        }
    }

    private static final class SessionFileDto {
        @JsonProperty("ticket")
        private String ticket;
        @JsonProperty("apiBase")
        private String apiBase;
        @JsonProperty("cookies")
        private Map<String, String> cookies;
    }
}
