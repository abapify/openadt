package org.openadt.config;

/** Unchecked OpenADT failure with an explicit type for Sonar java:S112. */
public class OpenAdtException extends RuntimeException {
    public OpenAdtException(String message) {
        super(message);
    }

    public OpenAdtException(String message, Throwable cause) {
        super(message, cause);
    }
}
