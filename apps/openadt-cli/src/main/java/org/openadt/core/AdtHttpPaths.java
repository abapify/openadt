package org.openadt.core;

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
        if (value.startsWith(SCHEME_HTTP_PREFIX) || value.startsWith(SCHEME_HTTPS_PREFIX)) {
            return value;
        }
        return SCHEME_HTTPS_PREFIX + value;
    }

    static boolean pathContainsAdtRoot(String path) {
        return path != null && path.contains(ADT_ICF_ROOT);
    }
}
