package org.openadt.sap.adt.sdk;

import java.lang.reflect.Method;

import org.openadt.config.CliLog;
/**
 * Ensures {@code com.sap.adt.communication} Activator is started when not running inside Eclipse.
 */
public final class AdtCommunicationBootstrap {
    private static volatile boolean prepared;

    private AdtCommunicationBootstrap() {
    }

    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        JCoEclipseBootstrap.prepare();
        try {
            Class<?> activatorClass = Class.forName("com.sap.adt.communication.internal.Activator");
            Method getDefault = activatorClass.getMethod("getDefault");
            Object framework = getDefault.invoke(null);
            if (framework == null) {
                throw new IllegalStateException("AdtCommunicationFrameworkPlugin.getDefault() returned null");
            }
            Object status = framework.getClass().getMethod("getInitializationStatus").invoke(framework);
            if (status != null) {
                boolean ok = (boolean) status.getClass().getMethod("isOK").invoke(status);
                if (!ok) {
                    String message = (String) status.getClass().getMethod("getMessage").invoke(status);
                    throw new IllegalStateException("ADT communication framework init failed: " + message);
                }
            }
            prepared = true;
            log("com.sap.adt.communication framework ready");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                "Failed to initialize ADT communication framework: " + error.getMessage(),
                error
            );
        }
    }

    private static void log(String message) {
        CliLog.sdk(message);
    }
}
