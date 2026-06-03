package org.openadt.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Active CLI context: which destination alias commands use when {@code <SYSTEM>} is omitted.
 */
public class SessionConfig {
    @JsonProperty("system")
    private String system;

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }
}
