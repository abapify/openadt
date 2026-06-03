package org.openadt.sap.adt.sdk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * String parameters for a registered SDK service invocation (from CLI flags).
 */
public record SdkServiceArgs(Map<String, String> values) {
    public SdkServiceArgs {
        values = values != null ? Map.copyOf(values) : Map.of();
    }

    public static SdkServiceArgs of(Map<String, String> values) {
        return new SdkServiceArgs(values);
    }

    public static SdkServiceArgs empty() {
        return new SdkServiceArgs(Map.of());
    }

    public String get(String key) {
        return values.get(key);
    }

    public String require(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing parameter: " + key);
        }
        return value.trim();
    }

    public String getOrDefault(String key, String defaultValue) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    public SdkServiceArgs withDefaults(Map<String, String> defaults) {
        Map<String, String> merged = new LinkedHashMap<>(defaults);
        merged.putAll(values);
        return new SdkServiceArgs(merged);
    }
}
