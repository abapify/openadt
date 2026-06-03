package org.openadt.sap.adt.sdk;

import java.lang.reflect.InvocationTargetException;

import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;

/**
 * Reflection entry for SDK-only services so distribution builds compile without SAP types in callers.
 */
public final class AdtSdkServiceGateway {
    private static final String DISCOVERY = "org.openadt.sap.adt.services.DiscoveryService";
    private static final String LOGON = "org.openadt.sap.adt.services.LogonService";
    private static final String CONTEXT = "org.openadt.sap.adt.services.SapAdtSessionContext";
    private static final String REGISTRY = "org.openadt.sap.adt.services.SdkServiceRegistry";

    private AdtSdkServiceGateway() {
    }

    public static AdtDiscoveryReport discover(
        OpenAdtConfig config,
        SystemProfile system,
        String collectionUri,
        String categoryTerm
    ) {
        try {
            Object context = openContext(config, system);
            Object service = Class.forName(DISCOVERY).getConstructor().newInstance();
            return (AdtDiscoveryReport) Class.forName(DISCOVERY)
                .getMethod("discover", Class.forName(CONTEXT), String.class, String.class)
                .invoke(service, context, collectionUri, categoryTerm);
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : error.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT SDK discovery failed: " + error.getMessage(), error);
        }
    }

    public static AdtLogonStatusReport logon(OpenAdtConfig config, SystemProfile system) {
        try {
            Object context = openContext(config, system);
            Object service = Class.forName(LOGON).getConstructor().newInstance();
            return (AdtLogonStatusReport) Class.forName(LOGON)
                .getMethod("logon", Class.forName(CONTEXT))
                .invoke(service, context);
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : error.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT SDK logon failed: " + error.getMessage(), error);
        }
    }

    public static SdkServiceResult invokeService(
        String serviceId,
        OpenAdtConfig config,
        SystemProfile system,
        SdkServiceArgs args
    ) {
        try {
            Object context = openContext(config, system);
            return (SdkServiceResult) Class.forName(REGISTRY)
                .getMethod("invoke", String.class, Object.class, SdkServiceArgs.class)
                .invoke(null, serviceId, context, args != null ? args : SdkServiceArgs.empty());
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throwAsRuntime(exception);
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : error.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT SDK service failed: " + error.getMessage(), error);
        }
    }

    @SuppressWarnings("unchecked")
    public static java.util.List<String> listSdkServices() {
        try {
            return (java.util.List<String>) Class.forName(REGISTRY).getMethod("serviceIds").invoke(null);
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT SDK service list failed: " + error.getMessage(), error);
        }
    }

    public static AdtDiscoveryDocument fetchDiscoveryDocument(OpenAdtConfig config, SystemProfile system) {
        try {
            Object context = openContext(config, system);
            Object service = Class.forName(DISCOVERY).getConstructor().newInstance();
            return (AdtDiscoveryDocument) Class.forName(DISCOVERY)
                .getMethod("fetchDiscoveryDocument", Class.forName(CONTEXT))
                .invoke(service, context);
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : error.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT SDK discovery document failed: " + error.getMessage(), error);
        }
    }

    public static java.util.List<String> logout(OpenAdtConfig config, SystemProfile destination) {
        try {
            return (java.util.List<String>) Class.forName("org.openadt.sap.adt.services.AuthSessionSupport")
                .getMethod("logout", OpenAdtConfig.class, SystemProfile.class)
                .invoke(null, config, destination);
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : error.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT auth logout failed: " + error.getMessage(), error);
        }
    }

    public static AdtLogonStatusReport logonStatus(OpenAdtConfig config, SystemProfile system) {
        try {
            Object context = openContext(config, system);
            Object service = Class.forName(LOGON).getConstructor().newInstance();
            return (AdtLogonStatusReport) Class.forName(LOGON)
                .getMethod("status", Class.forName(CONTEXT))
                .invoke(service, context);
        } catch (ClassNotFoundException error) {
            throw sdkUnavailable(error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : error.getMessage(), cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("ADT SDK logon status failed: " + error.getMessage(), error);
        }
    }

    private static Object openContext(OpenAdtConfig config, SystemProfile system) throws ReflectiveOperationException {
        return Class.forName(CONTEXT)
            .getMethod("open", OpenAdtConfig.class, SystemProfile.class)
            .invoke(null, config, system);
    }

    private static void throwAsRuntime(Exception error) {
        if (error instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new IllegalStateException(error.getMessage(), error);
    }

    private static IllegalStateException sdkUnavailable(ClassNotFoundException error) {
        return new IllegalStateException(
            "ADT SDK operations are not available in this OpenADT build. "
                + "Use transport sdk with runtime.adt_plugins_dir, or scripts/openadt-sdk.ps1.",
            error
        );
    }
}
