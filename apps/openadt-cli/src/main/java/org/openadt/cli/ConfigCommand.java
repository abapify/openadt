package org.openadt.cli;

import org.openadt.core.CliLog;
import org.openadt.core.ConfigLoader;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;
import org.openadt.setup.ConfigBootstrapRunner;
import org.openadt.setup.ConfigRuntimeBuilder;
import org.openadt.setup.SetupRuntimePreparer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "config",
    mixinStandardHelpOptions = true,
    description = "Show or manage OpenADT configuration",
    subcommands = {
        ConfigCommand.BootstrapCommand.class,
        ConfigCommand.BuildCommand.class,
        ConfigDestinationsCommand.class
    }
)
public class ConfigCommand implements Callable<Integer> {
    @Option(names = {"--config", "-c"}, scope = CommandLine.ScopeType.INHERIT, description = "Config file path")
    private Path configPath;

    @Override
    public Integer call() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        OpenAdtConfig config = loader.load(effectivePath);

        CliLog.info("Config: " + effectivePath);
        printSystems(config);
        printRuntime(config.getRuntime());
        printSecureLogin(config.getSecureLogin());
        printSdkRuntimeStatus();
        return 0;
    }

    Path getConfigPath() {
        return configPath;
    }

    private void printSystems(OpenAdtConfig config) {
        CliLog.info("\nSystems:");
        if (config.getSystems() == null || config.getSystems().isEmpty()) {
            CliLog.info("  (none configured)");
            return;
        }
        for (SystemProfile sys : config.getSystems()) {
            CliLog.info("  - %s (%s)%n",
                sys.getAlias() != null ? sys.getAlias() : sys.getSystemId(),
                sys.getSource() != null ? sys.getSource() : "config");
        }
    }

    private void printRuntime(OpenAdtConfig.RuntimeConfig runtime) {
        if (runtime == null) {
            return;
        }
        CliLog.info("\nRuntime:");
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

    private void printSecureLogin(OpenAdtConfig.SecureLoginConfig secureLogin) {
        if (secureLogin == null || secureLogin.getLocalSecurityHub() == null) {
            return;
        }
        CliLog.info("\nSecure login:");
        CliLog.info("  - local_security_hub: " + secureLogin.getLocalSecurityHub());
    }

    private void printSdkRuntimeStatus() throws IOException {
        String version = SetupRuntimePreparer.readInstalledVersion();
        boolean ready = SetupRuntimePreparer.runtimeJarReady(version);
        CliLog.info("\nSDK runtime:");
        CliLog.info("  - openadt version: " + version);
        CliLog.info("  - built: " + (ready ? "yes" : "no"));
        if (!ready) {
            CliLog.info("  - hint: run 'openadt config build' or 'openadt setup'");
        }
    }

    @Command(
        name = "bootstrap",
        mixinStandardHelpOptions = true,
        description = "Auto-detect SAP systems and runtime paths, then write config (no SDK build)"
    )
    static final class BootstrapCommand implements Callable<Integer> {
        @ParentCommand
        private ConfigCommand parent;

        @Option(names = {"--check"}, description = "Show detected values without saving config")
        private boolean check;

        @Override
        public Integer call() throws Exception {
            ConfigLoader loader = new ConfigLoader();
            ConfigBootstrapRunner.run(loader, parent.getConfigPath(), check);
            return 0;
        }
    }

    @Command(
        name = "build",
        mixinStandardHelpOptions = true,
        description = "Build full SAP SDK runtime jar for fetch/proxy using configured adt_plugins_dir"
    )
    static final class BuildCommand implements Callable<Integer> {
        @ParentCommand
        private ConfigCommand parent;

        @Option(names = {"--force"}, description = "Rebuild even when the runtime jar matches the installed OpenADT version")
        private boolean force;

        @Override
        public Integer call() throws Exception {
            return ConfigRuntimeBuilder.build(parent.getConfigPath(), force);
        }
    }
}
