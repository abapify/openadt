package org.openadt.bootstrap;

import org.openadt.config.CliLog;
import org.openadt.config.ConfigLoader;
import org.openadt.config.OpenAdtConfig;

import java.io.IOException;
import java.nio.file.Path;
public final class ConfigRuntimeBuilder {
    private ConfigRuntimeBuilder() {
    }

    public static int build(Path configPath, boolean force) throws IOException, InterruptedException {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultSetupConfigPath();
        OpenAdtConfig config = loader.load(effectivePath);
        String adtPluginsDir = config.getRuntime() != null ? config.getRuntime().getAdtPluginsDir() : null;
        if (!SetupRuntimePreparer.shouldPrepare(adtPluginsDir)) {
            CliLog.error("adt_plugins_dir is not configured. Run 'openadt config bootstrap' or 'openadt setup' first.");
            return 1;
        }
        String version = SetupRuntimePreparer.readInstalledVersion();
        if (!force && SetupRuntimePreparer.runtimeJarReady(version)) {
            CliLog.info("SDK runtime already built for OpenADT " + version + ".");
            CliLog.info("Use --force to rebuild.");
            return 0;
        }
        CliLog.info("Building full SAP SDK runtime for fetch/proxy...");
        return SetupRuntimePreparer.prepare(adtPluginsDir, version, force);
    }
}
