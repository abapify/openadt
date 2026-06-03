package org.openadt.config;

/**
 * Resolves the active system alias for commands after {@code openadt auth login}.
 */
public final class SessionContext {
    public static final String SYSTEM_ENV = "OPENADT_SYSTEM";

    private SessionContext() {
    }

    public static String resolveAlias(OpenAdtConfig config, String cliAlias) {
        if (cliAlias != null && !cliAlias.isBlank()) {
            return cliAlias.trim();
        }
        String fromEnv = System.getenv(SYSTEM_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        if (config != null && config.getSession() != null) {
            String fromConfig = config.getSession().getSystem();
            if (fromConfig != null && !fromConfig.isBlank()) {
                return fromConfig.trim();
            }
        }
        return null;
    }

    public static String requireAlias(OpenAdtConfig config, String cliAlias) {
        String alias = resolveAlias(config, cliAlias);
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException(
                "No active system context. Run: openadt auth login <SYSTEM> (or set OPENADT_SYSTEM)"
            );
        }
        return alias;
    }
}
