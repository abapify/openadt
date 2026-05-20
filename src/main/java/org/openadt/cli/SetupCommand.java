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
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();

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

        if (!check && !result.systems().isEmpty()) {
            OpenAdtConfig config = loader.load(effectivePath);
            config.setSystems(result.systems());
            loader.save(config, effectivePath);
            System.out.println("\nConfig saved to: " + effectivePath);
        }

        return 0;
    }
}
