package org.openadt.config;

/**
 * Profile resolution helpers for {@code fetch}: env override and actionable errors when SDK/JCo fails.
 */
public final class ProfileFetchHints {
    private ProfileFetchHints() {
    }

    /** CLI {@code --profile} wins; else {@code OPENADT_PROFILE}. */
    public static String resolveEffectiveProfile(String cliProfile) {
        if (cliProfile != null && !cliProfile.isBlank()) {
            return cliProfile.trim();
        }
        String fromEnv = System.getenv("OPENADT_PROFILE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return null;
    }

    public static String formatTransportError(SystemProfile destination, String explicitProfile, Throwable error) {
        String base = error.getMessage();
        if (base == null || base.isBlank()) {
            base = error.getClass().getSimpleName();
            Throwable cause = error.getCause();
            if (cause != null) {
                String causeMsg = cause.getMessage();
                base = causeMsg != null && !causeMsg.isBlank()
                    ? base + ": " + causeMsg
                    : base + " (" + cause.getClass().getSimpleName() + ")";
            }
        }
        if (explicitProfile != null && !explicitProfile.isBlank()) {
            return base;
        }
        if (destination == null) {
            return base;
        }
        String httpProfile = findHttpProfileName(destination);
        if (httpProfile == null) {
            return base;
        }
        String defaultProfile = destination.getDefaultProfile();
        if (defaultProfile == null || defaultProfile.isBlank() || httpProfile.equals(defaultProfile)) {
            return base;
        }
        boolean sdkFailure = base.contains("JCo Eclipse bridge")
            || base.contains("ADT SDK classpath")
            || base.contains("JCo jar not configured");
        if (!sdkFailure) {
            return base;
        }
        return base
            + "\nHint: default_profile="
            + defaultProfile
            + " uses SDK/JCo. For HTTP browser SSO: --profile="
            + httpProfile
            + " (or default_profile = \""
            + httpProfile
            + "\" / OPENADT_PROFILE="
            + httpProfile
            + ").";
    }

    static String findHttpProfileName(SystemProfile destination) {
        if (destination == null || destination.getProfiles() == null || destination.getProfiles().isEmpty()) {
            return null;
        }
        String fallback = null;
        for (var entry : destination.getProfiles().entrySet()) {
            if (!isHttpProfile(entry.getValue())) {
                continue;
            }
            if ("sso".equalsIgnoreCase(entry.getKey())) {
                return entry.getKey();
            }
            if (fallback == null) {
                fallback = entry.getKey();
            }
        }
        return fallback;
    }

    private static boolean isHttpProfile(SystemProfile.ProfileConfig profile) {
        if (profile == null) {
            return false;
        }
        if ("http".equalsIgnoreCase(profile.getTransport())) {
            return true;
        }
        return profile.getAdt() != null && "http".equalsIgnoreCase(profile.getAdt().getTransport());
    }
}
