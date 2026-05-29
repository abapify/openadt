package org.openadt.bootstrap;

import org.openadt.config.CliLog;
import org.openadt.config.ConfigLoader;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;

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

        CliLog.info("Detected systems:");
        if (result.systems().isEmpty()) {
            CliLog.info("  (none found)");
        } else {
            for (SystemProfile sys : result.systems()) {
                CliLog.info("  - %s (%s)%n",
                    sys.getAlias() != null ? sys.getAlias() : sys.getSystemId(),
                    sys.getSource());
            }
        }

        if (!result.warnings().isEmpty()) {
            CliLog.info("\nWarnings:");
            result.warnings().forEach(w -> CliLog.info("  ! " + w));
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
        CliLog.info("\nConfig saved to: " + effectivePath);

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
        CliLog.info("\nDetected runtime:");
        if (runtime.getJcoJar() != null) {
            CliLog.info("  - jco_jar: " + runtime.getJcoJar());
        }
        if (runtime.getJcoNativeDir() != null) {
            CliLog.info("  - jco_native_dir: " + runtime.getJcoNativeDir());
        }
        if (runtime.getSapcrypto() != null) {
            CliLog.info("  - sapcrypto: " + runtime.getSapcrypto());
        }
        if (runtime.getAdtPluginsDir() != null) {
            CliLog.info("  - adt_plugins_dir: " + runtime.getAdtPluginsDir());
        }
    }

    private static void printSecureLogin(OpenAdtConfig.SecureLoginConfig secureLogin) {
        if (secureLogin == null || secureLogin.getLocalSecurityHub() == null) {
            return;
        }
        CliLog.info("\nDetected secure login:");
        CliLog.info("  - local_security_hub: " + secureLogin.getLocalSecurityHub());
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
