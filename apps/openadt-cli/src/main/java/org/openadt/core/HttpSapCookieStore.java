package org.openadt.core;

import java.net.http.HttpHeaders;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SAP HTTP session cookies beyond {@code MYSAPSSO2} (e.g. {@code SAP_SESSIONID}, {@code SAPWL_*}) from {@code Set-Cookie}.
 */
public final class HttpSapCookieStore {
    private HttpSapCookieStore() {
    }

    public static Map<String, String> empty() {
        return new LinkedHashMap<>();
    }

    public static Map<String, String> copyOf(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return empty();
        }
        return new LinkedHashMap<>(source);
    }

    public static void merge(Map<String, String> target, Map<String, String> incoming) {
        if (target == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        target.putAll(incoming);
    }

    public static Map<String, String> fromSetCookieHeaders(HttpHeaders headers) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (headers == null) {
            return cookies;
        }
        headers.map().forEach((name, values) -> {
            if (name != null && "Set-Cookie".equalsIgnoreCase(name)) {
                for (String line : values) {
                    parseSetCookieLine(line, cookies);
                }
            }
        });
        return cookies;
    }

    static void parseSetCookieLine(String line, Map<String, String> cookies) {
        if (line == null || line.isBlank()) {
            return;
        }
        String pair = line.split(";", 2)[0].trim();
        int index = pair.indexOf('=');
        if (index <= 0) {
            return;
        }
        String cookieName = pair.substring(0, index).trim();
        String cookieValue = pair.substring(index + 1).trim();
        if (!cookieName.isEmpty() && !cookieValue.isEmpty()) {
            cookies.put(cookieName, cookieValue);
        }
    }

    public static String buildCookieHeader(String ticket, String client, Map<String, String> sessionCookies) {
        Map<String, String> merged = sessionCookies != null ? new LinkedHashMap<>(sessionCookies) : new LinkedHashMap<>();
        if (ticket != null && !ticket.isBlank()) {
            merged.put("MYSAPSSO2", ticket.trim());
        }
        StringBuilder header = new StringBuilder();
        merged.forEach((name, value) -> {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append(name).append('=').append(value);
        });
        if (client != null && !client.isBlank()) {
            if (header.length() > 0) {
                header.append("; ");
            }
            header.append("sap-usercontext=sap-client=").append(client.trim());
        }
        return header.toString();
    }

    public static String describeNames(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return "(none)";
        }
        Set<String> names = new LinkedHashSet<>(cookies.keySet());
        names.remove("MYSAPSSO2");
        if (names.isEmpty()) {
            return "MYSAPSSO2 only";
        }
        return names.stream().sorted().collect(Collectors.joining(", "));
    }
}
