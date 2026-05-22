package org.openadt.core;

import java.util.Map;

public record ProxyResponse(
    String version,
    int statusCode,
    String reasonPhrase,
    Map<String, String> headers,
    byte[] body
) {
    public String getHeader(String name) {
        return headers.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}
