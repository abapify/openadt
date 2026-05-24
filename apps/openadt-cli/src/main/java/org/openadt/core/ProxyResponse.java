package org.openadt.core;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProxyResponse other)) {
            return false;
        }
        return Objects.equals(version, other.version)
            && statusCode == other.statusCode
            && Objects.equals(reasonPhrase, other.reasonPhrase)
            && Objects.equals(headers, other.headers)
            && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, statusCode, reasonPhrase, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "ProxyResponse[version=" + version + ", statusCode=" + statusCode
            + ", reasonPhrase=" + reasonPhrase + ", headers=" + headers
            + ", body=" + Arrays.toString(body) + "]";
    }
}
