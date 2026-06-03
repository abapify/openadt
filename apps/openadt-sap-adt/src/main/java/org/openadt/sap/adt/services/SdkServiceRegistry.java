package org.openadt.sap.adt.services;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.openadt.config.OpenAdtException;
import org.openadt.sap.adt.sdk.SdkServiceArgs;
import org.openadt.sap.adt.sdk.SdkServiceResult;

/**
 * Maps service id → handler class. Handler classes live in {@code handlers.*} and are loaded reflectively
 * so distribution builds compile without SAP types in callers.
 */
public final class SdkServiceRegistry {
    private static final String CONTEXT = "org.openadt.sap.adt.services.SapAdtSessionContext";

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

    public static SdkServiceResult invoke(String serviceId, Object context, SdkServiceArgs args) {
        String handlerClass = HANDLER_CLASSES.get(serviceId);
        if (handlerClass == null) {
            throw new IllegalArgumentException(
                "Unknown SDK service '" + serviceId + "'. Known: " + String.join(", ", serviceIds())
            );
        }
        try {
            return invokeHandler(handlerClass, context, args);
        } catch (ReflectiveOperationException error) {
            throw new OpenAdtException("SDK service invocation failed: " + error.getMessage(), error);
        }
    }

    private static SdkServiceResult invokeHandler(String handlerClass, Object context, SdkServiceArgs args)
        throws ReflectiveOperationException {
        if (!handlerClass.startsWith("org.openadt.sap.adt.services.handlers.")) {
            throw new SecurityException("Handler class must be from trusted package: " + handlerClass);
        }
        try {
            Class<?> handlerType = Class.forName(handlerClass);
            Object handler = handlerType.getConstructor().newInstance();
            Class<?> contextType = Class.forName(CONTEXT);
            Method execute = handlerType.getMethod("execute", contextType, SdkServiceArgs.class);
            Object result = execute.invoke(handler, context, args);
            if (!(result instanceof SdkServiceResult serviceResult)) {
                throw new IllegalStateException(handlerClass + " did not return SdkServiceResult");
            }
            return serviceResult;
        } catch (ClassNotFoundException error) {
            throw new OpenAdtException(
                "SDK handler not available in this build: " + handlerClass,
                error
            );
        }
    }
}
