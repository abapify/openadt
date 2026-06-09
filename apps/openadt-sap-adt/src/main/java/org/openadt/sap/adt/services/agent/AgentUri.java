package org.openadt.sap.adt.services.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight ADT object URI parser/normalizer for agent-foundation services.
 *
 * <p>Most LSP extension methods accept either an absolute ADT URI
 * ({@code /sap/bc/adt/oo/classes/zcl_foo}) or a typed object reference
 * ({@code class:/sap/bc/adt/oo/classes/zcl_foo}, or shorthand
 * {@code class:zcl_foo}). We normalize both into a single shape that the SDK
 * services can consume.</p>
 *
 * <p>This is intentionally <em>not</em> a full ADT URI parser; it only needs
 * enough to extract the ADT type prefix and the resource path for
 * {@code IAdtObjectReference} and friends.</p>
 */
public final class AgentUri {

    private final String raw;
    private final String type;        // e.g. "class", "include", "program", null if not typed
    private final String path;        // e.g. "/sap/bc/adt/oo/classes/zcl_foo"
    private final String name;        // last path segment, decoded
    private final Map<String, String> query; // preserved, decoded

    private AgentUri(String raw, String type, String path, String name, Map<String, String> query) {
        this.raw = raw;
        this.type = type;
        this.path = path;
        this.name = name;
        this.query = query;
    }

    /**
     * Parse a string into an {@link AgentUri}. Returns null on any failure
     * (null/blank input, URI syntax error, missing path). Callers wrap the
     * null result in {@link AgentError#invalidUri(String)} as appropriate.
     */
    public static AgentUri parseOrNull(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.trim();
        String type = null;
        int colon = s.indexOf(':');
        if (colon > 0 && !s.startsWith("/") && !s.regionMatches(0, "http", 0, 4)) {
            String head = s.substring(0, colon);
            // only treat as type if the head is purely alphabetic and short — guards
            // against URLs like "http://host/sap/bc/adt/..."
            if (head.length() <= 32 && head.chars().allMatch(Character::isLetterOrDigit)
                && Character.isLetter(head.charAt(0))) {
                type = head.toLowerCase();
                s = s.substring(colon + 1);
            }
        }
        URI uri;
        try {
            uri = new URI(s);
        } catch (URISyntaxException error) {
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        String name = path;
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            name = path.substring(slash + 1);
        }
        Map<String, String> query = new LinkedHashMap<>();
        if (uri.getQuery() != null) {
            for (String pair : uri.getQuery().split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                query.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return new AgentUri(input, type, path, name, query);
    }

    public String raw() {
        return raw;
    }

    public String type() {
        return type;
    }

    public String path() {
        return path;
    }

    public String name() {
        return name;
    }

    public Map<String, String> query() {
        return query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentUri other)) {
            return false;
        }
        return Objects.equals(raw, other.raw)
            && Objects.equals(type, other.type)
            && Objects.equals(path, other.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, type, path);
    }
}
