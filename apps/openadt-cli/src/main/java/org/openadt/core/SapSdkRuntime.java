package org.openadt.core;

/**
 * One-time preparation for ADT SDK + JCo + Secure Login before fetch/proxy requests.
 */
public final class SapSdkRuntime {
    private static volatile boolean prepared;

    private SapSdkRuntime() {
    }

    public static void prepare(OpenAdtConfig config, SystemProfile system) {
        if (config == null || config.getRuntime() == null) {
            throw new IllegalStateException("Runtime not configured. Run 'openadt setup' first.");
        }
        if (prepared) {
            return;
        }
        synchronized (SapSdkRuntime.class) {
            if (prepared) {
                return;
            }
            JCoRuntimeBootstrap.prepare(config.getRuntime());
            AdtCommunicationBootstrap.prepare();
            String systemId = system != null && system.getSystemId() != null
                ? system.getSystemId()
                : (system != null ? system.getAlias() : null);
            SecureLoginBootstrap.prepareForJco(
                config,
                SecureLoginBootstrap.hubBrowserMonitorEnabled(),
                false,
                false,
                systemId
            );
            prepared = true;
        }
    }

    /** Reset for tests only. */
    static void resetForTests() {
        prepared = false;
        AdtSdkTransportClient.resetLogonCacheForTests();
    }
}
