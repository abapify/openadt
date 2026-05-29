package org.openadt.sap.adt.fallback.http;

/**
 * Default {@code Accept} values for ADT HTTP requests.
 */
public final class AdtAcceptHeaders {
    private AdtAcceptHeaders() {
    }

    public static String[] defaultAccept(String uri) {
        if (uri != null && uri.contains("/sap/bc/adt/core/http/systeminformation")) {
            return new String[]{"application/vnd.sap.adt.core.http.systeminformation.v1+json"};
        }
        if (uri != null && isDiscoveryPath(uri)) {
            return new String[]{"application/atomsvc+xml"};
        }
        return new String[]{
            "application/atom+xml;type=feed",
            "application/xml",
            "application/vnd.sap.adt.core+xml"
        };
    }

    private static boolean isDiscoveryPath(String uri) {
        return uri.contains("/sap/bc/adt/discovery") || uri.contains("/sap/bc/adt/core/discovery");
    }

    public static String defaultAcceptHeaderValue(String uri) {
        return String.join(", ", defaultAccept(uri));
    }
}
