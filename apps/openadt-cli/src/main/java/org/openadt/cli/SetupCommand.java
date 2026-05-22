package org.openadt.cli;

import org.openadt.core.ConfigLoader;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;
import org.openadt.setup.SetupAnalyzer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "setup",
    mixinStandardHelpOptions = true,
    description = "Auto-detect SAP systems and configure OpenADT"
)
public class SetupCommand implements Callable<Integer> {
    @Option(names = {"--config", "-c"}, description = "Config file path")
    private Path configPath;

    @Option(names = {"--check"}, description = "Show detected systems and validate without saving config")
    private boolean check;

    @Override
    public Integer call() throws Exception {
        ConfigLoader loader = new ConfigLoader();
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

        if (!check && hasDetectedData(result)) {
            OpenAdtConfig config = loader.load(effectivePath);
            if (config.getVersion() <= 0) {
                config.setVersion(1);
            }
            config.setSystems(result.systems());
            mergeRuntime(config, result.runtime());
            mergeSecureLogin(config, result.secureLogin());
            loader.saveSetupConfig(config, effectivePath);
            System.out.println("\nConfig saved to: " + effectivePath);
        }

        return 0;
    }

    private boolean hasDetectedData(SetupAnalyzer.SetupResult result) {
        return !result.systems().isEmpty() || result.runtime() != null || result.secureLogin() != null;
    }

    private void printRuntime(OpenAdtConfig.RuntimeConfig runtime) {
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

    private void printSecureLogin(OpenAdtConfig.SecureLoginConfig secureLogin) {
        if (secureLogin == null || secureLogin.getLocalSecurityHub() == null) {
            return;
        }
        System.out.println("\nDetected secure login:");
        System.out.println("  - local_security_hub: " + secureLogin.getLocalSecurityHub());
    }

    private void mergeRuntime(OpenAdtConfig config, OpenAdtConfig.RuntimeConfig detectedRuntime) {
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

    private void mergeSecureLogin(OpenAdtConfig config, OpenAdtConfig.SecureLoginConfig detectedSecureLogin) {
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
