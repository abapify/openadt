package org.openadt.core;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts {@code com.sap.conn.jco.eclipse} outside the Eclipse workbench so ADT SDK
 * {@link com.sap.adt.communication.internal.jco.JCoDestinationRegistry} can use
 * {@code Registry.getDestinationDataRegistry()}.
 */
public final class JCoEclipseBootstrap {
    private static final String ACTIVATOR_CLASS = "com.sap.mw.jco3.eclipse.internal.Activator";
    private static final String ENVIRONMENT_CLASS = "com.sap.conn.jco.ext.Environment";
    private static final ConcurrentHashMap<String, VarHandle> FIELD_HANDLES = new ConcurrentHashMap<>();
    private static volatile boolean prepared;

    private JCoEclipseBootstrap() {
    }

    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        try {
            Class<?> activatorClass = Class.forName(ACTIVATOR_CLASS);
            if (getPluginInstance(activatorClass) != null) {
                prepared = true;
                return;
            }
            Object activator = activatorClass.getDeclaredConstructor().newInstance();
            registerJcoEnvironment(activator);
            markJcoRegistrationSuccessful(activator);
            setPluginInstance(activatorClass, activator);
            prepared = true;
            log("com.sap.conn.jco.eclipse initialized for headless ADT SDK");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                "Failed to initialize SAP JCo Eclipse bridge (com.sap.conn.jco.eclipse): "
                    + error.getMessage(),
                error
            );
        }
    }

    private static void registerJcoEnvironment(Object activator) throws ReflectiveOperationException {
        Class<?> environmentClass = Class.forName(ENVIRONMENT_CLASS);
        registerProvider(
            environmentClass,
            "registerDestinationDataProvider",
            "com.sap.conn.jco.ext.DestinationDataProvider",
            activator,
            "destinationDataProvider"
        );
        registerProvider(
            environmentClass,
            "registerSessionReferenceProvider",
            "com.sap.conn.jco.ext.SessionReferenceProvider",
            activator,
            "sessionReferenceProvider"
        );
        registerProvider(
            environmentClass,
            "registerClientPassportManager",
            "com.sap.conn.jco.ext.ClientPassportManager",
            activator,
            "clientPassportManager"
        );
    }

    private static void registerProvider(
        Class<?> environmentClass,
        String registerMethod,
        String providerInterface,
        Object activator,
        String activatorField
    ) throws ReflectiveOperationException {
        Object provider = readField(activator, activatorField);
        Class<?> providerClass = Class.forName(providerInterface);
        Method method = environmentClass.getMethod(registerMethod, providerClass);
        try {
            method.invoke(null, provider);
        } catch (ReflectiveOperationException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IllegalStateException) {
                log("JCo Environment." + registerMethod + " already registered (continuing)");
            } else {
                throw error;
            }
        }
    }

    private static void markJcoRegistrationSuccessful(Object activator) throws ReflectiveOperationException {
        writeBooleanField(activator, "successfullJCORegistration", true);
    }

    private static Object readField(Object target, String fieldName) throws ReflectiveOperationException {
        return privateFieldHandle(target.getClass(), fieldName).get(target);
    }

    private static Object getPluginInstance(Class<?> activatorClass) throws ReflectiveOperationException {
        return privateFieldHandle(activatorClass, "plugin").get();
    }

    private static void setPluginInstance(Class<?> activatorClass, Object activator) throws ReflectiveOperationException {
        privateFieldHandle(activatorClass, "plugin").set(activator);
    }

    private static void writeBooleanField(Object target, String fieldName, boolean value) throws ReflectiveOperationException {
        privateFieldHandle(target.getClass(), fieldName).set(target, value);
    }

    private static VarHandle privateFieldHandle(Class<?> owner, String fieldName) throws ReflectiveOperationException {
        String cacheKey = owner.getName() + '#' + fieldName;
        VarHandle cached = FIELD_HANDLES.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(owner, MethodHandles.lookup());
        VarHandle handle = lookup.unreflectVarHandle(owner.getDeclaredField(fieldName));
        VarHandle existing = FIELD_HANDLES.putIfAbsent(cacheKey, handle);
        return existing != null ? existing : handle;
    }

    private static void log(String message) {
        if (!Boolean.parseBoolean(System.getenv().getOrDefault("OPENADT_VERBOSE", "true"))) {
            return;
        }
        CliLog.error("[openadt sdk] " + message);
        CliLog.stderr().flush();
    }
}
