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
        String adtPluginsDir = resolveAdtPluginsDir(loader, configPath);
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

    /**
     * Reads {@code adt_plugins_dir} using the same resolution as {@code openadt config}, so a
     * directory-local {@code .openadt/config.toml} is honoured instead of only the global file.
     *
     * <p>When no explicit path is given and the directory-local config carries no
     * {@code adt_plugins_dir}, the global setup config is tried as well: {@code openadt setup} writes
     * detected runtime paths there, and it must not be shadowed by an unrelated project config.
     */
    private static String resolveAdtPluginsDir(ConfigLoader loader, Path configPath) throws IOException {
        if (configPath != null) {
            return adtPluginsDirOf(loader, configPath);
        }
        String fromDefault = adtPluginsDirOf(loader, loader.getDefaultConfigPath());
        if (fromDefault != null && !fromDefault.isBlank()) {
            return fromDefault;
        }
        return adtPluginsDirOf(loader, loader.getDefaultSetupConfigPath());
    }

    private static String adtPluginsDirOf(ConfigLoader loader, Path path) throws IOException {
        OpenAdtConfig config = loader.load(path);
        return config.getRuntime() != null ? config.getRuntime().getAdtPluginsDir() : null;
    }
}
