package org.openadt.setup;

import org.openadt.core.ConfigLoader;
import org.openadt.core.OpenAdtConfig;

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
            System.err.println("adt_plugins_dir is not configured. Run 'openadt config bootstrap' or 'openadt setup' first.");
            return 1;
        }
        String version = SetupRuntimePreparer.readInstalledVersion();
        if (!force && SetupRuntimePreparer.runtimeJarReady(version)) {
            System.out.println("SDK runtime already built for OpenADT " + version + ".");
            System.out.println("Use --force to rebuild.");
            return 0;
        }
        System.out.println("Building full SAP SDK runtime for fetch/proxy...");
        return SetupRuntimePreparer.prepare(adtPluginsDir, version);
    }
}
