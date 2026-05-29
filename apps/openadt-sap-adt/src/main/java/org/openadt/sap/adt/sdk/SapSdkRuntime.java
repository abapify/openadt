package org.openadt.sap.adt.sdk;


import org.openadt.config.OpenAdtConfig;
import org.openadt.sap.adt.bootstrap.JCoRuntimeBootstrap;
import org.openadt.sap.adt.bootstrap.SecureLoginBootstrap;
/**
 * One-time preparation for ADT SDK + JCo + Secure Login before fetch/proxy requests.
 */
public final class SapSdkRuntime {
    private static volatile boolean prepared;

    private SapSdkRuntime() {
    }

    public static void prepare(OpenAdtConfig config) {
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
            SecureLoginBootstrap.prepareForJco(
                config,
                SecureLoginBootstrap.hubBrowserMonitorEnabled(),
                false,
                false
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
