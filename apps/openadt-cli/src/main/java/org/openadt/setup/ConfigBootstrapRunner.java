package org.openadt.setup;

import org.openadt.core.ConfigLoader;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;

import java.nio.file.Path;

public final class ConfigBootstrapRunner {
    public record Outcome(boolean saved, String adtPluginsDir) {
    }

    private ConfigBootstrapRunner() {
    }

    public static Outcome run(ConfigLoader loader, Path configPath, boolean checkOnly) throws Exception {
        Path effectivePath = configPath != null ? configPath : loader.getDefaultSetupConfigPath();
        SetupAnalyzer analyzer = new SetupAnalyzer();
        SetupAnalyzer.SetupResult result = analyzer.analyze();

        System.out.println("Detected systems:");
        if (result.systems().isEmpty()) {
            System.out.println("  (none found)");
        } else {
            for (SystemProfile sys : result.systems()) {
                System.out.printf("  - %s (%s)%n",
                    sys.getAlias() != null ? sys.getAlias() : sys.getSystemId(),
                    sys.getSource());
            }
        }

        if (!result.warnings().isEmpty()) {
            System.out.println("\nWarnings:");
            result.warnings().forEach(w -> System.out.println("  ! " + w));
        }

        printRuntime(result.runtime());
        printSecureLogin(result.secureLogin());

        if (checkOnly || !hasDetectedData(result)) {
            return new Outcome(false, detectedAdtPluginsDir(result));
        }

        OpenAdtConfig config = loader.load(effectivePath);
        if (config.getVersion() <= 0) {
            config.setVersion(1);
        }
        config.setSystems(result.systems());
        mergeRuntime(config, result.runtime());
        mergeSecureLogin(config, result.secureLogin());
        loader.saveSetupConfig(config, effectivePath);
        System.out.println("\nConfig saved to: " + effectivePath);

        String adtPluginsDir = config.getRuntime() != null ? config.getRuntime().getAdtPluginsDir() : null;
        return new Outcome(true, adtPluginsDir);
    }

    private static boolean hasDetectedData(SetupAnalyzer.SetupResult result) {
        return !result.systems().isEmpty() || result.runtime() != null || result.secureLogin() != null;
    }

    private static String detectedAdtPluginsDir(SetupAnalyzer.SetupResult result) {
        return result.runtime() != null ? result.runtime().getAdtPluginsDir() : null;
    }

    private static void printRuntime(OpenAdtConfig.RuntimeConfig runtime) {
        if (runtime == null) {
            return;
        }
        System.out.println("\nDetected runtime:");
        if (runtime.getJcoJar() != null) {
            System.out.println("  - jco_jar: " + runtime.getJcoJar());
        }
        if (runtime.getJcoNativeDir() != null) {
            System.out.println("  - jco_native_dir: " + runtime.getJcoNativeDir());
        }
        if (runtime.getSapcrypto() != null) {
            System.out.println("  - sapcrypto: " + runtime.getSapcrypto());
        }
        if (runtime.getAdtPluginsDir() != null) {
            System.out.println("  - adt_plugins_dir: " + runtime.getAdtPluginsDir());
        }
    }

    private static void printSecureLogin(OpenAdtConfig.SecureLoginConfig secureLogin) {
        if (secureLogin == null || secureLogin.getLocalSecurityHub() == null) {
            return;
        }
        System.out.println("\nDetected secure login:");
        System.out.println("  - local_security_hub: " + secureLogin.getLocalSecurityHub());
    }

    private static void mergeRuntime(OpenAdtConfig config, OpenAdtConfig.RuntimeConfig detectedRuntime) {
        if (detectedRuntime == null) {
            return;
        }
        OpenAdtConfig.RuntimeConfig runtime = config.getRuntime();
        if (runtime == null) {
            runtime = new OpenAdtConfig.RuntimeConfig();
            config.setRuntime(runtime);
        }
        if (detectedRuntime.getJcoJar() != null) {
            runtime.setJcoJar(detectedRuntime.getJcoJar());
        }
        if (detectedRuntime.getJcoNativeDir() != null) {
            runtime.setJcoNativeDir(detectedRuntime.getJcoNativeDir());
        }
        if (detectedRuntime.getSapcrypto() != null) {
            runtime.setSapcrypto(detectedRuntime.getSapcrypto());
        }
        if (detectedRuntime.getAdtPluginsDir() != null) {
            runtime.setAdtPluginsDir(detectedRuntime.getAdtPluginsDir());
        }
    }

    private static void mergeSecureLogin(OpenAdtConfig config, OpenAdtConfig.SecureLoginConfig detectedSecureLogin) {
        if (detectedSecureLogin == null) {
            return;
        }
        OpenAdtConfig.SecureLoginConfig secureLogin = config.getSecureLogin();
        if (secureLogin == null) {
            secureLogin = new OpenAdtConfig.SecureLoginConfig();
            config.setSecureLogin(secureLogin);
        }
        if (secureLogin.getLocalSecurityHub() == null) {
            secureLogin.setLocalSecurityHub(detectedSecureLogin.getLocalSecurityHub());
        }
        if (secureLogin.getOrigin() == null) {
            secureLogin.setOrigin(detectedSecureLogin.getOrigin());
        }
        if (secureLogin.getReferer() == null) {
            secureLogin.setReferer(detectedSecureLogin.getReferer());
        }
    }
}
