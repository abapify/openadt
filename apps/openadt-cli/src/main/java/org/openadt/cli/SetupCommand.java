package org.openadt.cli;

import org.openadt.core.CliLog;
import org.openadt.core.ConfigLoader;
import org.openadt.setup.ConfigBootstrapRunner;
import org.openadt.setup.ConfigRuntimeBuilder;
import org.openadt.setup.SetupRuntimePreparer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "setup",
    mixinStandardHelpOptions = true,
    description = "Bootstrap config and build SDK runtime (config bootstrap + config build)"
)
public class SetupCommand implements Callable<Integer> {
    @Option(names = {"--config", "-c"}, description = "Config file path")
    private Path configPath;

    @Option(names = {"--check"}, description = "Show detected systems and validate without saving config or building")
    private boolean check;

    @Option(names = {"--skip-build"}, description = "Save config without building the SDK runtime jar")
    private boolean skipBuild;

    @Override
    public Integer call() throws Exception {
        ConfigLoader loader = new ConfigLoader();
        ConfigBootstrapRunner.Outcome outcome = ConfigBootstrapRunner.run(loader, configPath, check);
        if (check || skipBuild) {
            return 0;
        }
        if (!SetupRuntimePreparer.shouldPrepare(outcome.adtPluginsDir())) {
            CliLog.info("\nNo adt_plugins_dir detected; skipping SDK runtime build.");
            CliLog.info("fetch/proxy with SDK transport requires Eclipse ADT plugins.");
            return 0;
        }
        return ConfigRuntimeBuilder.build(configPath, false);
    }
}
