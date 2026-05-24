package org.openadt.core;

import java.util.function.UnaryOperator;

/**
 * Resolves HTTP TLS trust material for a destination. Per-destination {@code adt} settings
 * override global {@code [runtime]} and environment variables.
 */
final class HttpTlsTrustResolver {
    private HttpTlsTrustResolver() {
    }

    static String resolveCaCert(OpenAdtConfig config, SystemProfile system, UnaryOperator<String> env) {
        String fromAdt = adt(system, SystemProfile.AdtConfig::getHttpCaCert);
        if (fromAdt != null) {
            return fromAdt;
        }
        return runtime(config, OpenAdtConfig.RuntimeConfig::getHttpCaCert, env, "OPENADT_HTTP_CA_CERT");
    }

    static String resolveTruststore(OpenAdtConfig config, SystemProfile system, UnaryOperator<String> env) {
        String fromAdt = adt(system, SystemProfile.AdtConfig::getHttpTruststore);
        if (fromAdt != null) {
            return fromAdt;
        }
        return runtime(
            config,
            OpenAdtConfig.RuntimeConfig::getHttpTruststore,
            env,
            "OPENADT_HTTP_TRUSTSTORE"
        );
    }

    static String resolveTruststorePassword(OpenAdtConfig config, SystemProfile system, UnaryOperator<String> env) {
        String fromAdt = adt(system, SystemProfile.AdtConfig::getHttpTruststorePassword);
        if (fromAdt != null) {
            return fromAdt;
        }
        return runtime(
            config,
            OpenAdtConfig.RuntimeConfig::getHttpTruststorePassword,
            env,
            "OPENADT_HTTP_TRUSTSTORE_PASSWORD"
        );
    }

    static String trustCacheKey(OpenAdtConfig config, SystemProfile system, UnaryOperator<String> env) {
        return String.join(
            "\0",
            nullToEmpty(resolveCaCert(config, system, env)),
            nullToEmpty(resolveTruststore(config, system, env)),
            nullToEmpty(resolveTruststorePassword(config, system, env))
        );
    }

    private static String adt(
        SystemProfile system,
        java.util.function.Function<SystemProfile.AdtConfig, String> getter
    ) {
        if (system == null || system.getAdt() == null) {
            return null;
        }
        return blankToNull(getter.apply(system.getAdt()));
    }

    private static String runtime(
        OpenAdtConfig config,
        java.util.function.Function<OpenAdtConfig.RuntimeConfig, String> getter,
        UnaryOperator<String> env,
        String envKey
    ) {
        String value = null;
        if (config != null && config.getRuntime() != null) {
            value = blankToNull(getter.apply(config.getRuntime()));
        }
        if (value != null) {
            return value;
        }
        return blankToNull(env.apply(envKey));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
