package org.openadt.core;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public final class AdtTransportFactory {
    private AdtTransportFactory() {
    }

    public static AdtTransportClient create(OpenAdtConfig config, SystemProfile system) throws Exception {
        String transport = system.getAdt() != null ? system.getAdt().getTransport() : null;
        if ("http".equalsIgnoreCase(transport)) {
            return new HttpAdtTransportClient(config);
        }
        if ("sdk".equalsIgnoreCase(transport) && !hasAdtPluginsDir(config)) {
            throw new IllegalStateException("ADT SDK transport requires runtime.adt_plugins_dir to be configured.");
        }
        boolean sdkExplicit = transport == null
            || "sdk".equalsIgnoreCase(transport)
            || transport.isBlank();
        if (sdkExplicit
            && !"rest-rfc".equalsIgnoreCase(transport)
            && hasAdtPluginsDir(config)) {
            return createSdkTransportClient(config);
        }

        if (config.getRuntime() == null || config.getRuntime().getJcoJar() == null) {
            throw new IllegalStateException("JCo jar not configured. Run 'openadt setup' first.");
        }

        JCoRuntimeBootstrap.prepare(config.getRuntime());
        JCoDestinationFactory factory = JCoDestinationFactory.fromJarPath(
            Path.of(config.getRuntime().getJcoJar()),
            config.getRuntime()
        );
        return new AdtRestRfcClient(factory);
    }

    private static boolean hasAdtPluginsDir(OpenAdtConfig config) {
        return config.getRuntime() != null
            && config.getRuntime().getAdtPluginsDir() != null
            && !config.getRuntime().getAdtPluginsDir().isBlank();
    }

    private static AdtTransportClient createSdkTransportClient(OpenAdtConfig config) throws Exception {
        try {
            Class<?> sdkClass = Class.forName("org.openadt.core.AdtSdkTransportClient");
            return (AdtTransportClient) sdkClass.getConstructor(OpenAdtConfig.class).newInstance(config);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                "ADT SDK transport is not available in this OpenADT build. "
                    + "Use transport = \"rest-rfc\" or \"http\", or run with scripts/openadt-sdk.ps1 and Eclipse ADT plugins."
            );
        } catch (InvocationTargetException e) {
            throw wrapSdkBootstrapFailure(e.getCause());
        } catch (LinkageError error) {
            throw wrapSdkBootstrapFailure(error);
        }
    }

    private static IllegalStateException wrapSdkBootstrapFailure(Throwable cause) {
        String hint =
            "ADT SDK classpath is incomplete for this launch (java -jar cannot load all Eclipse/ADT bundles). "
                + "For HTTP SSO use: --profile=sso. "
                + "For SNC/SDK use: nx run openadt-cli:run-sdk -- … or scripts/openadt-sdk.ps1, "
                + "or nx run openadt-cli:run -- … (dev launcher adds sap-lib classpath when --profile is omitted).";
        if (cause instanceof Exception exception) {
            return new IllegalStateException(hint + " " + exception.getMessage(), exception);
        }
        return new IllegalStateException(hint + " " + cause, cause);
    }
}
