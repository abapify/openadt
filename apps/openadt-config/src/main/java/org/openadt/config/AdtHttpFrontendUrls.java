package org.openadt.config;

import java.net.URI;
import java.util.Locale;

/**
 * HTTP ADT frontend addressing: {@code base_url} (scheme + host [+ port]) for the SAP frontend
 * origin and a fixed ICF path {@value #ADT_API_PATH} for API calls. Not
 * {@code /sap/bc/adt/discovery}.
 */
public final class AdtHttpFrontendUrls {
    public static final String ADT_API_PATH = "/sap/bc/adt";

    private AdtHttpFrontendUrls() {
    }

    public static String resolveFrontendOrigin(SystemProfile.AdtConfig adt) {
        if (adt == null) {
            return null;
        }
        String raw = adt.getBaseUrl();
        if (raw == null || raw.isBlank()) {
            if (adt.getAshost() != null && !adt.getAshost().isBlank()) {
                raw = "https://" + adt.getAshost().trim();
            }
        }
        return normalizeToOrigin(raw);
    }

    public static String resolveAdtApiBase(SystemProfile.AdtConfig adt) {
        String origin = resolveFrontendOrigin(adt);
        if (origin == null) {
            return null;
        }
        return trimTrailingSlash(origin) + ADT_API_PATH;
    }

    public static String normalizeToOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = withHttpsSchemeIfMissing(raw.trim());
        URI uri = URI.create(value);
        if (uri.getScheme() == null || uri.getScheme().isBlank()) {
            throw new IllegalArgumentException("base_url must include scheme (https://): " + raw);
        }
        if (uri.getAuthority() == null || uri.getAuthority().isBlank()) {
            throw new IllegalArgumentException("base_url must include host[:port]: " + raw);
        }
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getAuthority() + "/";
    }

    private static String withHttpsSchemeIfMissing(String value) {
        if (value.contains("://")) {
            return value;
        }
        return "https://" + value;
    }

    private static String trimTrailingSlash(String origin) {
        if (origin.endsWith("/")) {
            return origin.substring(0, origin.length() - 1);
        }
        return origin;
    }
}
