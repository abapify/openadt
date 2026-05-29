package org.openadt.sap.adt.fallback.http;

import java.util.Locale;

/**
 * Well-known SAP ADT ICF paths and HTTP scheme prefixes (fixed by ADT protocol, not per-landscape).
 */
public final class AdtHttpPaths {
    public static final String SCHEME_HTTP_PREFIX = "http://";
    public static final String SCHEME_HTTPS_PREFIX = "https://";
    public static final String ADT_ICF_ROOT = "/sap/bc/adt";
    public static final String ADT_DISCOVERY = ADT_ICF_ROOT + "/discovery";

    private AdtHttpPaths() {
    }

    static String withHttpsSchemeIfMissing(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(SCHEME_HTTP_PREFIX) || normalized.startsWith(SCHEME_HTTPS_PREFIX)) {
            return value;
        }
        return SCHEME_HTTPS_PREFIX + value;
    }

    static boolean pathContainsAdtRoot(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).contains(ADT_ICF_ROOT);
    }
}
