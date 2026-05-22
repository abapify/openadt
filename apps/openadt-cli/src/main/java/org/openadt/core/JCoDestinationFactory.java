package org.openadt.core;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

public class JCoDestinationFactory {
    private static final String JCO_DEST_MANAGER = "com.sap.conn.jco.JCoDestinationManager";
    private static final String JCO_DEST_DATA_PROVIDER = "com.sap.conn.jco.ext.DestinationDataProvider";

    private final ClassLoader jcoClassLoader;
    private final OpenAdtConfig.RuntimeConfig runtimeConfig;

    public JCoDestinationFactory(ClassLoader jcoClassLoader) {
        this(jcoClassLoader, null);
    }

    public JCoDestinationFactory(ClassLoader jcoClassLoader, OpenAdtConfig.RuntimeConfig runtimeConfig) {
        this.jcoClassLoader = jcoClassLoader;
        this.runtimeConfig = runtimeConfig;
    }

    public static JCoDestinationFactory fromJarPath(Path jarPath) throws Exception {
        return fromJarPath(jarPath, null);
    }

    public static JCoDestinationFactory fromJarPath(Path jarPath, OpenAdtConfig.RuntimeConfig runtimeConfig) throws Exception {
        Path canonical = JCoJarCanonicalizer.canonicalize(jarPath);
        URL jarUrl = canonical.toUri().toURL();
        URLClassLoader loader = new URLClassLoader(new URL[]{jarUrl},
            JCoDestinationFactory.class.getClassLoader());
        return new JCoDestinationFactory(loader, runtimeConfig);
    }

    public Object getDestination(SystemProfile system) throws Exception {
        Properties props = buildProperties(system);
        String destName = system.getAlias() != null ? system.getAlias() : "openadt_dest";

        Class<?> destManagerClass = Class.forName(JCO_DEST_MANAGER, true, jcoClassLoader);
        Class<?> jcoEnvironmentClass = Class.forName(
            "com.sap.conn.jco.ext.Environment", true, jcoClassLoader);

        // Register an in-memory destination provider
        Object provider = createInMemoryProvider(destName, props);
        Method registerMethod = jcoEnvironmentClass.getMethod("registerDestinationDataProvider",
            Class.forName(JCO_DEST_DATA_PROVIDER, true, jcoClassLoader));
        registerMethod.invoke(null, provider);

        Method getDestMethod = destManagerClass.getMethod("getDestination", String.class);
        return getDestMethod.invoke(null, destName);
    }

    private Object createInMemoryProvider(String destName, Properties props) throws Exception {
        // Use a dynamic proxy to implement DestinationDataProvider
        Class<?> providerInterface = Class.forName(JCO_DEST_DATA_PROVIDER, true, jcoClassLoader);
        return java.lang.reflect.Proxy.newProxyInstance(
            jcoClassLoader,
            new Class[]{providerInterface},
            (proxy, method, args) -> {
                String methodName = method.getName();
                if ("getDestinationProperties".equals(methodName)) {
                    return props;
                }
                if ("supportsEvents".equals(methodName)) {
                    return false;
                }
                return null;
            }
        );
    }

    Properties buildProperties(SystemProfile system) {
        Properties props = new Properties();
        SystemProfile.JcoConfig jco = system.getJco();
        if (jco != null) {
            if (jco.getAshost() != null) props.setProperty("jco.client.ashost", jco.getAshost());
            if (jco.getSysnr() != null) props.setProperty("jco.client.sysnr", jco.getSysnr());
            if (jco.getMshost() != null) props.setProperty("jco.client.mshost", jco.getMshost());
            if (jco.getMsserv() != null) props.setProperty("jco.client.msserv", jco.getMsserv());
            if (jco.getR3name() != null) props.setProperty("jco.client.r3name", jco.getR3name());
            if (jco.getGroup() != null) props.setProperty("jco.client.group", jco.getGroup());
            if (jco.getSncMode() != null) props.setProperty("jco.client.snc_mode", jco.getSncMode());
            if (jco.getSncQop() != null) props.setProperty("jco.client.snc_qop", jco.getSncQop());
            if (jco.getSncPartnername() != null) props.setProperty("jco.client.snc_partnername", jco.getSncPartnername());
            if (jco.getSncSso() != null) props.setProperty("jco.client.snc_sso", jco.getSncSso());
            if (jco.getSticky() != null) props.setProperty("jco.client.sticky", jco.getSticky());
            if (jco.getDenyInitialPassword() != null) {
                props.setProperty("jco.client.deny_initial_password", jco.getDenyInitialPassword());
            }
        }
        if (system.getClient() != null) props.setProperty("jco.client.client", system.getClient());
        if (system.getUser() != null) {
            props.setProperty("jco.client.user", system.getUser());
            props.setProperty("jco.destination.userid", system.getUser());
            props.setProperty("jco.destination.auth_type", "CONFIGURED_USER");
        }
        if (system.getLanguage() != null) props.setProperty("jco.client.lang", system.getLanguage());

        if (runtimeConfig != null && runtimeConfig.getSapcrypto() != null) {
            props.setProperty("jco.client.snc_lib", runtimeConfig.getSapcrypto());
        }

        String destinationId = buildDestinationId(system);
        if (destinationId != null) {
            props.setProperty("jco.client.destination", destinationId);
        }

        SystemProfile.AdtConfig adt = system.getAdt();
        if (adt != null) {
            if (adt.getAshost() != null) props.setProperty("adt.ashost", adt.getAshost());
            if (adt.getAuthenticationKind() != null) {
                props.setProperty("adt.jco.client.authenticationKind", adt.getAuthenticationKind());
            }
            if (destinationId != null) {
                props.setProperty("adt.jco.client.destination", destinationId);
            }
            if (jco != null && jco.getR3name() != null) {
                props.setProperty("adt.r3name", jco.getR3name());
            } else if (system.getSystemId() != null) {
                props.setProperty("adt.r3name", system.getSystemId());
            }
        }
        return props;
    }

    private String buildDestinationId(SystemProfile system) {
        if (system.getSystemId() == null || system.getClient() == null
            || system.getUser() == null || system.getLanguage() == null) {
            return null;
        }
        return system.getSystemId()
            + "_" + system.getClient()
            + "_" + system.getUser().toLowerCase(Locale.ROOT)
            + "_" + system.getLanguage().toLowerCase(Locale.ROOT);
    }
}
