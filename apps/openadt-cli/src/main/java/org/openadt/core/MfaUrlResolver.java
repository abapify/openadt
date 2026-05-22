package org.openadt.core;

import org.openadt.setup.SapRulesDetector;

import java.util.Locale;

/**
 * Resolves which URL to open in the user's browser for interactive MFA.
 */
public final class MfaUrlResolver {
    private MfaUrlResolver() {
    }

    /**
     * ADT/SAML frontend first (what Eclipse uses for HTTP logon), then Secure Login portal — not raw {@code doLogin}.
     */
    public static String resolveBrowserUrl(OpenAdtConfig config, String systemId) {
        String discovery = resolveAdtDiscoveryUrl(config, systemId);
        if (discovery != null) {
            return discovery;
        }
        OpenAdtConfig.SecureLoginConfig secureLogin = SecureLoginBootstrap.resolveSecureLogin(config);
        return resolveSecureLoginPortalUrl(secureLogin);
    }

    public static String resolveAdtDiscoveryUrl(OpenAdtConfig config, String systemId) {
        if (systemId != null && !systemId.isBlank() && config != null && config.getSystems() != null) {
            for (SystemProfile system : config.getSystems()) {
                if (!matchesSystem(system, systemId)) {
                    continue;
                }
                if (system.getAdt() != null && system.getAdt().getDiscoveryUrl() != null
                    && !system.getAdt().getDiscoveryUrl().isBlank()) {
                    return system.getAdt().getDiscoveryUrl().trim();
                }
            }
        }
        String normalized = systemId == null ? null : systemId.trim().toUpperCase(Locale.ROOT);
        for (SystemProfile detected : new SapRulesDetector().detect()) {
            if (normalized != null && !normalized.isBlank()
                && normalized.equalsIgnoreCase(detected.getSystemId())
                && detected.getAdt() != null
                && detected.getAdt().getDiscoveryUrl() != null
                && !detected.getAdt().getDiscoveryUrl().isBlank()) {
                return detected.getAdt().getDiscoveryUrl().trim();
            }
        }
        return null;
    }

    /**
     * Portal MFA URL from registry {@code ssoURL} (WebClientSettings profile).
     * Do not use hub {@code enrollURL0} ({@code doLogin}) or hub profile id here — SAP stores different UUIDs.
     */
    public static String resolveSecureLoginPortalUrl(OpenAdtConfig.SecureLoginConfig secureLogin) {
        if (secureLogin == null) {
            return null;
        }
        if (secureLogin.getSsoUrl() != null && !secureLogin.getSsoUrl().isBlank()) {
            return secureLogin.getSsoUrl().trim();
        }
        String portalProfileId = profileIdFromUrl(secureLogin.getSsoUrl());
        String origin = secureLogin.getOrigin();
        if (portalProfileId == null) {
            portalProfileId = profileIdFromUrl(secureLogin.getEnrollUrl());
        }
        if (portalProfileId != null && origin != null && !origin.isBlank()) {
            String base = origin.trim();
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "/SecureLoginServer/portal/webclient?profile=" + portalProfileId;
        }
        return null;
    }

    static String profileIdFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("profile=([0-9a-fA-F-]{36})")
            .matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean matchesSystem(SystemProfile system, String query) {
        if (system == null || query == null) {
            return false;
        }
        return query.equalsIgnoreCase(system.getAlias())
            || query.equalsIgnoreCase(system.getSystemId());
    }
}
