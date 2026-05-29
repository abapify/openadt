package org.openadt.sap.adt.sdk;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public record ProxyRequest(
    String method,
    String uri,
    String version,
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
        if (!(obj instanceof ProxyRequest other)) {
            return false;
        }
        return Objects.equals(method, other.method)
            && Objects.equals(uri, other.uri)
            && Objects.equals(version, other.version)
            && Objects.equals(headers, other.headers)
            && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, uri, version, headers, Arrays.hashCode(body));
    }

    @Override
    public String toString() {
        return "ProxyRequest[method=" + method + ", uri=" + uri + ", version=" + version
            + ", headers=" + headers + ", body=" + Arrays.toString(body) + "]";
    }
}
