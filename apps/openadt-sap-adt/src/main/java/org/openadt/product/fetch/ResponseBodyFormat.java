package org.openadt.product.fetch;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Detects response body format for {@code openadt fetch} output handling. */
public final class ResponseBodyFormat {
    private ResponseBodyFormat() {
    }

    public static boolean isJson(Map<String, String> headers, byte[] body) {
        String contentType = headerValue(headers, "Content-Type");
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("json")) {
                return true;
            }
            if (lower.contains("xml") || lower.contains("atom") || lower.contains("html")) {
                return false;
            }
        }
        if (body == null || body.length == 0) {
            return false;
        }
        String trimmed = new String(body, StandardCharsets.UTF_8).stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    public static boolean isXml(Map<String, String> headers, byte[] body) {
        String contentType = headerValue(headers, "Content-Type");
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("xml") || lower.contains("atom")) {
                return true;
            }
            if (lower.contains("json") || lower.contains("html")) {
                return false;
            }
        }
        if (body == null || body.length == 0) {
            return false;
        }
        String trimmed = new String(body, StandardCharsets.UTF_8).stripLeading();
        return trimmed.startsWith("<?xml") || trimmed.startsWith("<");
    }

    private static String headerValue(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
