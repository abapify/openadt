package org.openadt.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Resolves the {@code MYSAPSSO2} value used by HTTP ADT transport.
 * <p>
 * HTTP ADT against an ICF/SAML frontend requires a browser-issued SAP logon ticket cookie.
 * The Secure Login Web Adapter prepares SNC credentials for JCo/GUI; it does not replace
 * {@code MYSAPSSO2} for direct HTTP calls unless you supply the ticket explicitly.
 */
public class AdtHttpCookieProvider {
    private final UnaryOperator<String> envProvider;
    private final AdtHttpTicketProvider ticketProvider;
    private final HttpSsoTicketCache ticketCache;
    private volatile boolean lastResolveUsedDiskCache;
    private volatile Map<String, String> lastSessionCookies = HttpSapCookieStore.empty();

    public AdtHttpCookieProvider() {
        this(System::getenv, null, new HttpSsoTicketCache());
    }

    AdtHttpCookieProvider(UnaryOperator<String> envProvider) {
        this(envProvider, null, new HttpSsoTicketCache());
    }

    AdtHttpCookieProvider(UnaryOperator<String> envProvider, AdtHttpTicketProvider ticketProvider) {
        this(envProvider, ticketProvider, new HttpSsoTicketCache());
    }

    AdtHttpCookieProvider(
        UnaryOperator<String> envProvider,
        AdtHttpTicketProvider ticketProvider,
        HttpSsoTicketCache ticketCache
    ) {
        this.envProvider = envProvider;
        this.ticketProvider = ticketProvider != null
            ? ticketProvider
            : new AdtHttpReentranceTicketFlow(envProvider, AdtHttpReentranceTicketFlow::openInDesktopBrowser);
        this.ticketCache = ticketCache;
    }

    public String resolveMysapsso2(OpenAdtConfig config, SystemProfile system) {
        lastResolveUsedDiskCache = false;
        lastSessionCookies = HttpSapCookieStore.empty();
        String alias = system != null && system.getAlias() != null ? system.getAlias() : "?";
        String fromEnv = blankToNull(envProvider.apply("OPENADT_MYSAPSSO2"));
        if (fromEnv != null) {
            CliLog.httpSso("ticket source: OPENADT_MYSAPSSO2 env");
            return fromEnv;
        }

        if (config != null && config.getSecureLogin() != null) {
            String fromConfig = blankToNull(config.getSecureLogin().getMysapsso2());
            if (fromConfig != null) {
                CliLog.httpSso("ticket source: secure_login.mysapsso2 in config");
                return fromConfig;
            }
        }

        String cookieFile = blankToNull(envProvider.apply("OPENADT_COOKIE_FILE"));
        if (cookieFile != null) {
            String fromFile = readCookieFile(Path.of(cookieFile));
            if (fromFile != null) {
                CliLog.httpSso("ticket source: OPENADT_COOKIE_FILE");
                return fromFile;
            }
            CliLog.httpSso("OPENADT_COOKIE_FILE set but empty or unreadable");
        }

        Optional<HttpSsoTicketCache.CachedSession> cached = ticketCache.readSession(system);
        if (cached.isPresent()) {
            lastResolveUsedDiskCache = true;
            lastSessionCookies = HttpSapCookieStore.copyOf(cached.get().cookiesOrEmpty());
            HttpSsoTicketCache.CachedSession session = cached.get();
            CliLog.httpSso(
                "ticket source: disk cache for " + alias
                    + (session.hasApiBase() ? " (api base cached)" : "")
                    + "; cookies: " + HttpSapCookieStore.describeNames(session.cookiesOrEmpty())
            );
            return session.ticket();
        }

        CliLog.httpSso("ticket source: browser callback (disk cache miss)");
        ensureWebAdapterReady(config);
        String fromReentranceFlow = ticketProvider.acquireTicket(config, system);
        if (fromReentranceFlow != null && !fromReentranceFlow.isBlank()) {
            Map<String, String> cookies = HttpSapSessionWarmup.probe(config, system, fromReentranceFlow);
            lastSessionCookies = HttpSapCookieStore.copyOf(cookies);
            ticketCache.writeSession(system, new HttpSsoTicketCache.CachedSession(fromReentranceFlow, null, cookies));
            return fromReentranceFlow;
        }

        throw new IllegalStateException(buildMissingTicketMessage(system));
    }

    private void ensureWebAdapterReady(OpenAdtConfig config) {
        if (config == null || config.getSecureLogin() == null) {
            return;
        }
        String profileId = resolveWebAdapterProfileId(config.getSecureLogin());
        if (profileId == null) {
            return;
        }
        if (config.getSecureLogin().getOrigin() == null || config.getSecureLogin().getOrigin().isBlank()) {
            return;
        }
        SecureLoginHubClient hub = new SecureLoginHubClient(config.getSecureLogin());
        if (!hub.isReachable()) {
            return;
        }
        try {
            hub.ensureWebAdapterLoggedIn(profileId);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Secure Login hub is reachable but Web Adapter login could not be verified: " + error.getMessage(),
                error
            );
        } catch (IOException error) {
            throw new IllegalStateException(
                "Secure Login hub is reachable but Web Adapter login could not be verified: " + error.getMessage(),
                error
            );
        }
    }

    static String resolveWebAdapterProfileId(OpenAdtConfig.SecureLoginConfig secureLogin) {
        if (secureLogin == null) {
            return null;
        }
        return blankToNull(secureLogin.getWebAdapterProfileId());
    }

    private String readCookieFile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                return null;
            }
            if (content.startsWith("MYSAPSSO2=")) {
                return content.substring("MYSAPSSO2=".length()).trim();
            }
            return content;
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read OPENADT_COOKIE_FILE: " + path, error);
        }
    }

    private String buildMissingTicketMessage(SystemProfile system) {
        String discovery = system != null && system.getAdt() != null ? system.getAdt().getDiscoveryUrl() : null;
        StringBuilder message = new StringBuilder(
            "HTTP ADT transport requires a SAP logon ticket (MYSAPSSO2). "
                + "Set OPENADT_MYSAPSSO2, secure_login.mysapsso2 in config, or OPENADT_COOKIE_FILE. "
        );
        if (discovery != null && !discovery.isBlank()) {
            message.append("Sign in to ").append(discovery).append(" in a browser and export the MYSAPSSO2 cookie value.");
        } else {
            message.append("Configure destinations.<alias>.adt.discovery_url and sign in through that frontend in a browser.");
        }
        return message.toString();
    }

    public void invalidateCachedTicket(SystemProfile system) {
        lastResolveUsedDiskCache = false;
        lastSessionCookies = HttpSapCookieStore.empty();
        ticketCache.invalidate(system);
    }

    public boolean lastResolveUsedDiskCache() {
        return lastResolveUsedDiskCache;
    }

    public Map<String, String> lastSessionCookies() {
        return HttpSapCookieStore.copyOf(lastSessionCookies);
    }

    public void recordResponseCookies(SystemProfile system, Map<String, String> fromResponse) {
        if (fromResponse == null || fromResponse.isEmpty()) {
            return;
        }
        HttpSapCookieStore.merge(lastSessionCookies, fromResponse);
        ticketCache.mergeCookies(system, fromResponse);
    }

    HttpSsoTicketCache ticketCacheForTransport() {
        return ticketCache;
    }

    private static String profileSuffix(SystemProfile system) {
        if (system == null || system.getActiveProfile() == null || system.getActiveProfile().isBlank()) {
            return "";
        }
        return " profile=" + system.getActiveProfile();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
