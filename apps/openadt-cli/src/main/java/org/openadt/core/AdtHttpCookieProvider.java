package org.openadt.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Resolves the {@code MYSAPSSO2} value used by HTTP ADT transport.
 * <p>
 * HTTP ADT against an ICF/SAML frontend requires a browser-issued SAP logon ticket cookie.
 * The Secure Login Web Adapter prepares SNC credentials for JCo/GUI; it does not replace
 * {@code MYSAPSSO2} for direct HTTP calls unless you supply the ticket explicitly.
 */
public class AdtHttpCookieProvider {
    private final Function<String, String> envProvider;

    public AdtHttpCookieProvider() {
        this(System::getenv);
    }

    AdtHttpCookieProvider(Function<String, String> envProvider) {
        this.envProvider = envProvider;
    }

    public String resolveMysapsso2(OpenAdtConfig config, SystemProfile system) {
        String fromEnv = blankToNull(envProvider.apply("OPENADT_MYSAPSSO2"));
        if (fromEnv != null) {
            return fromEnv;
        }

        if (config != null && config.getSecureLogin() != null) {
            String fromConfig = blankToNull(config.getSecureLogin().getMysapsso2());
            if (fromConfig != null) {
                return fromConfig;
            }
        }

        String cookieFile = blankToNull(envProvider.apply("OPENADT_COOKIE_FILE"));
        if (cookieFile != null) {
            String fromFile = readCookieFile(Path.of(cookieFile));
            if (fromFile != null) {
                return fromFile;
            }
        }

        ensureWebAdapterReady(config);

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
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
