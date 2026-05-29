package org.openadt.sap.adt.bootstrap;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.sap.adt.fallback.http.AdtHttpCookieProvider;
import org.openadt.sap.adt.fallback.http.MfaBrowserLauncher;
import org.openadt.sap.adt.fallback.http.MfaUrlResolver;
/**
 * Prepares Secure Login Web Adapter for JCo/SNC before ADT SDK {@code ensureLoggedOn}.
 */
public final class SecureLoginBootstrap {
    private static final Pattern PROFILE_ID_PATTERN = Pattern.compile("profile=([0-9a-fA-F-]{36})");
    private static final Pattern ORIGIN_PATTERN = Pattern.compile("(https?://[^/]+)");

    private SecureLoginBootstrap() {
    }

    public static void prepareForJco(OpenAdtConfig config) {
        prepareForJco(config, hubBrowserMonitorEnabled(), false, false);
    }

    public static void prepareForJco(OpenAdtConfig config, boolean hubBrowserMonitor) {
        prepareForJco(config, hubBrowserMonitor, false, false);
    }

    public static void prepareForJco(
        OpenAdtConfig config,
        boolean hubBrowserMonitor,
        boolean openMfaBrowser,
        boolean forceHubLogin
    ) {
        OpenAdtConfig.SecureLoginConfig secureLogin = resolveSecureLogin(config);
        if (secureLogin == null) {
            return;
        }
        String profileId = resolveWebAdapterProfileId(secureLogin);
        if (profileId == null) {
            return;
        }
        SecureLoginHubClient hub = new SecureLoginHubClient(secureLogin);
        if (!hub.isReachable()) {
            log("Secure Login hub not reachable at " + secureLogin.getLocalSecurityHub());
            return;
        }
        try {
            String statusBefore = hub.webAdapterStatus(profileId);
            log("Secure Login Web Adapter status before login: " + statusBefore);
            if (openMfaBrowser || forceHubLogin) {
                openWebAdapterBrowser(config);
            } else if ("LOGGED_IN".equalsIgnoreCase(statusBefore)) {
                log("Web Adapter already LOGGED_IN — skipping portal browser.");
            }
            log("Secure Login hub: ensuring Web Adapter LOGGED_IN (profile " + profileId
                + ", browserMonitor=" + hubBrowserMonitor + ", forceLogin=" + forceHubLogin + ")");
            if (hubBrowserMonitor) {
                log("If MFA is required, Secure Login should open a browser window via the hub.");
            }
            hub.ensureWebAdapterLoggedIn(profileId, hubBrowserMonitor, forceHubLogin);
            log("Secure Login Web Adapter status: LOGGED_IN");
        } catch (IOException error) {
            throw new IllegalStateException(
                "Secure Login hub login failed: " + error.getMessage(),
                error
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Secure Login hub login failed: " + error.getMessage(),
                error
            );
        }
    }

    public static OpenAdtConfig.SecureLoginConfig resolveSecureLogin(OpenAdtConfig config) {
        OpenAdtConfig.SecureLoginConfig fromRegistry = SecureLoginRegistryReader.read().orElse(null);
        OpenAdtConfig.SecureLoginConfig fromConfig = config != null ? config.getSecureLogin() : null;
        return mergeSecureLogin(fromRegistry, fromConfig);
    }

    private static OpenAdtConfig.SecureLoginConfig mergeSecureLogin(
        OpenAdtConfig.SecureLoginConfig registry,
        OpenAdtConfig.SecureLoginConfig fileConfig
    ) {
        if (registry == null) {
            return fileConfig;
        }
        if (fileConfig == null) {
            return registry;
        }
        OpenAdtConfig.SecureLoginConfig merged = new OpenAdtConfig.SecureLoginConfig();
        merged.setLocalSecurityHub(firstNonBlank(fileConfig.getLocalSecurityHub(), registry.getLocalSecurityHub()));
        merged.setOrigin(firstNonBlank(fileConfig.getOrigin(), registry.getOrigin()));
        merged.setReferer(firstNonBlank(fileConfig.getReferer(), registry.getReferer()));
        merged.setWebAdapterProfileId(firstNonBlank(fileConfig.getWebAdapterProfileId(), registry.getWebAdapterProfileId()));
        merged.setEnrollUrl(firstNonBlank(registry.getEnrollUrl(), fileConfig.getEnrollUrl()));
        merged.setSsoUrl(firstNonBlank(registry.getSsoUrl(), fileConfig.getSsoUrl()));
        merged.setMysapsso2(fileConfig.getMysapsso2());
        return merged;
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    static String resolveWebAdapterProfileId(OpenAdtConfig.SecureLoginConfig secureLogin) {
        return AdtHttpCookieProvider.resolveWebAdapterProfileId(secureLogin);
    }

    /**
     * Like Eclipse: browser for Secure Login Web Adapter only (certificate / hub MFA), not for ADT logon.
     */
    public static void openWebAdapterBrowser(OpenAdtConfig config) {
        OpenAdtConfig.SecureLoginConfig secureLogin = resolveSecureLogin(config);
        String url = MfaUrlResolver.resolveSecureLoginPortalUrl(secureLogin);
        if (url == null) {
            throw new IllegalStateException(
                "No Secure Login portal URL in registry (ssoURL). Sign in via SAP Secure Login Client (Web Adapter profile)."
            );
        }
        if (!url.contains("profile=") || MfaUrlResolver.profileIdFromUrl(url) == null) {
            throw new IllegalStateException(
                "Secure Login portal URL has no profile id (would show Configuration [] on SLS): " + url
            );
        }
        try {
            log("Opening browser for Secure Login portal (ssoURL / WebClientSettings): " + url);
            MfaBrowserLauncher.open(url);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to open Web Adapter browser: " + error.getMessage(), error);
        }
    }

    /**
     * Optional: ADT/SAML front door for HTTP transport ({@code MYSAPSSO2}) only — Eclipse does not use this for SDK/JCo ADT.
     */
    public static void openAdtSamlBrowser(OpenAdtConfig config, String systemId) {
        String url = MfaUrlResolver.resolveAdtDiscoveryUrl(config, systemId);
        if (url == null) {
            throw new IllegalStateException(
                "No ADT base_url for " + systemId + ". HTTP transport only; SDK logon uses JCo/SNC like Eclipse."
            );
        }
        try {
            log("Opening browser for ADT/SAML (HTTP cookie path, not Eclipse SDK): " + url);
            MfaBrowserLauncher.open(url);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to open ADT browser: " + error.getMessage(), error);
        }
    }

    public static boolean hubBrowserMonitorEnabled() {
        String value = System.getenv("OPENADT_HUB_BROWSER");
        if (value == null || value.isBlank()) {
            return true;
        }
        return !"0".equals(value.trim()) && !"false".equalsIgnoreCase(value.trim());
    }

    private static void log(String message) {
        CliLog.sdk(message);
    }

    /**
     * Reads Web Adapter profile id and SLS origin from Windows Secure Login registry (user profiles).
     */
    static final class SecureLoginRegistryReader {
        private SecureLoginRegistryReader() {
        }

        static java.util.Optional<OpenAdtConfig.SecureLoginConfig> read() {
            if (!isWindows()) {
                return java.util.Optional.empty();
            }
            try {
                String output = queryRegistry();
                if (output == null) {
                    return java.util.Optional.empty();
                }
                return parseRegistryOutput(output);
            } catch (IOException error) {
                return java.util.Optional.empty();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return java.util.Optional.empty();
            }
        }

        private static String queryRegistry() throws IOException, InterruptedException {
            Process process = new ProcessBuilder(
                "reg", "query", "HKCU\\Software\\SAP\\SecureLogin\\groups\\user\\profiles", "/s"
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            if (process.exitValue() != 0 || !output.contains("enrollURL")) {
                return null;
            }
            return output;
        }

        private static java.util.Optional<OpenAdtConfig.SecureLoginConfig> parseRegistryOutput(String output) {
            String webAdapterSection = extractWebAdapterSection(output);
            String enrollUrl = parseRegistryUrl(webAdapterSection, "enrollURL0");
            String ssoUrl = parseRegistryUrl(webAdapterSection, "ssoURL");
            String profileId = resolveProfileId(webAdapterSection, enrollUrl);
            if (profileId == null) {
                return java.util.Optional.empty();
            }
            String origin = resolveOrigin(ssoUrl, enrollUrl, webAdapterSection);
            OpenAdtConfig.SecureLoginConfig secureLogin = new OpenAdtConfig.SecureLoginConfig();
            secureLogin.setLocalSecurityHub(SecureLoginHubClient.DEFAULT_HUB_URL);
            if (origin != null) {
                secureLogin.setOrigin(origin);
                secureLogin.setReferer(origin + "/");
            }
            secureLogin.setWebAdapterProfileId(profileId);
            secureLogin.setEnrollUrl(enrollUrl);
            secureLogin.setSsoUrl(ssoUrl);
            return java.util.Optional.of(secureLogin);
        }

        private static String extractWebAdapterSection(String output) {
            int webAdapterIndex = output.indexOf("profiles\\Web Adapter");
            if (webAdapterIndex < 0) {
                webAdapterIndex = output.indexOf("profiles/Web Adapter");
            }
            if (webAdapterIndex < 0) {
                return output;
            }
            return output.substring(webAdapterIndex, Math.min(output.length(), webAdapterIndex + 4096));
        }

        private static String resolveProfileId(String webAdapterSection, String enrollUrl) {
            String profileId = profileIdFromUrl(enrollUrl);
            if (profileId != null) {
                return profileId;
            }
            Matcher profileMatcher = PROFILE_ID_PATTERN.matcher(webAdapterSection);
            return profileMatcher.find() ? profileMatcher.group(1) : null;
        }

        private static String resolveOrigin(String ssoUrl, String enrollUrl, String webAdapterSection) {
            String originSource = firstNonNull(ssoUrl, enrollUrl, webAdapterSection);
            Matcher originMatcher = ORIGIN_PATTERN.matcher(originSource);
            return originMatcher.find() ? originMatcher.group(1) : null;
        }

        private static String firstNonNull(String primary, String secondary, String fallback) {
            if (primary != null) {
                return primary;
            }
            if (secondary != null) {
                return secondary;
            }
            return fallback;
        }

        private static String profileIdFromUrl(String url) {
            if (url == null || url.isBlank()) {
                return null;
            }
            Matcher matcher = PROFILE_ID_PATTERN.matcher(url);
            return matcher.find() ? matcher.group(1) : null;
        }

        private static String parseRegistryUrl(String section, String key) {
            Pattern sameLine = Pattern.compile(
                key + "\\s+REG_SZ\\s+(https?://\\S+)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher sameLineMatch = sameLine.matcher(section);
            if (sameLineMatch.find()) {
                return sameLineMatch.group(1).trim();
            }
            Pattern nextLine = Pattern.compile(
                key + "\\s+REG_SZ\\s*[\\r\\n]+\\s*(https?://\\S+)",
                Pattern.CASE_INSENSITIVE
            );
            Matcher nextLineMatch = nextLine.matcher(section);
            if (nextLineMatch.find()) {
                return nextLineMatch.group(1).trim();
            }
            return null;
        }

        private static boolean isWindows() {
            return System.getProperty("os.name", "").toLowerCase().contains("win");
        }
    }
}
