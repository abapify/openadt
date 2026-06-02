package org.openadt.sap.adt.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.openadt.config.OpenAdtException;
import org.openadt.sap.adt.sdk.SdkServiceArgs;
import org.openadt.sap.adt.sdk.SdkServiceResult;

/**
 * Maps service id → handler class. Handler classes live in {@code handlers.*} and are loaded reflectively
 * so distribution builds without SAP types on the compile classpath still compile this registry.
 */
public final class SdkServiceRegistry {
    private static final Map<String, String> HANDLER_CLASSES = new LinkedHashMap<>();

    static {
        register("discovery.document", "org.openadt.sap.adt.services.handlers.DiscoveryDocumentHandler");
        register("transport.list", "org.openadt.sap.adt.services.handlers.TransportListHandler");
    }

    private SdkServiceRegistry() {
    }

    public static void register(String serviceId, String handlerClassName) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        if (handlerClassName == null || handlerClassName.isBlank()) {
            throw new IllegalArgumentException("handlerClassName is required");
        }
        HANDLER_CLASSES.put(serviceId.trim(), handlerClassName.trim());
    }

    public static List<String> serviceIds() {
        return List.copyOf(new TreeSet<>(HANDLER_CLASSES.keySet()));
    }

    public static SdkServiceResult invoke(String serviceId, SapAdtSessionContext context, SdkServiceArgs args)
        throws Exception {
        String handlerClass = HANDLER_CLASSES.get(serviceId);
        if (handlerClass == null) {
            throw new IllegalArgumentException(
                "Unknown SDK service '" + serviceId + "'. Known: " + String.join(", ", serviceIds())
            );
        }
        SdkServiceHandler handler = loadHandler(handlerClass);
        return handler.execute(context, args);
    }

    private static SdkServiceHandler loadHandler(String handlerClass) {
        try {
            Class<?> type = Class.forName(handlerClass);
            Object instance = type.getConstructor().newInstance();
            if (!(instance instanceof SdkServiceHandler sdkHandler)) {
                throw new IllegalStateException(handlerClass + " does not implement SdkServiceHandler");
            }
            return sdkHandler;
        } catch (ClassNotFoundException error) {
            throw new OpenAdtException(
                "SDK handler not available in this build: " + handlerClass,
                error
            );
        } catch (ReflectiveOperationException error) {
            throw new OpenAdtException("Failed to load SDK handler " + handlerClass + ": " + error.getMessage(), error);
        }
    }
}
