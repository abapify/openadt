package org.openadt.cli;

import org.openadt.core.AdtRestRfcClient;
import org.openadt.core.ConfigLoader;
import org.openadt.core.JCoDestinationFactory;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;
import org.openadt.proxy.LocalAdtProxyServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "proxy",
    mixinStandardHelpOptions = true,
    description = "Start the local ADT proxy server"
)
public class ProxyCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "System alias to proxy", arity = "0..1")
    private String systemAlias;

    @Option(names = {"--port", "-p"}, description = "Port to listen on (default: 8080)", defaultValue = "8080")
    private int port;

    @Option(names = {"--config", "-c"}, description = "Config file path")
    private Path configPath;

    @Override
    public Integer call() throws Exception {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        OpenAdtConfig config = loader.load(effectivePath);

        SystemProfile system = findSystem(config, systemAlias);
        if (system == null) {
            System.err.println("System not found: " + systemAlias);
            return 1;
        }

        if (config.getRuntime() == null || config.getRuntime().getJcoJar() == null) {
            System.err.println("JCo jar not configured. Run 'openadt setup' first.");
            return 1;
        }

        JCoDestinationFactory factory = JCoDestinationFactory.fromJarPath(
            Path.of(config.getRuntime().getJcoJar()));
        AdtRestRfcClient rfcClient = new AdtRestRfcClient(factory);
        LocalAdtProxyServer proxyServer = new LocalAdtProxyServer(config, rfcClient);
        proxyServer.start(system, port);

        System.out.println("Press Ctrl+C to stop.");
        Runtime.getRuntime().addShutdownHook(new Thread(proxyServer::stop));
        Thread.currentThread().join();

        return 0;
    }

    private SystemProfile findSystem(OpenAdtConfig config, String alias) {
        if (config.getSystems() == null) return null;
        if (alias == null && !config.getSystems().isEmpty()) {
            return config.getSystems().get(0);
        }
        return config.getSystems().stream()
            .filter(s -> alias.equals(s.getAlias()))
            .findFirst()
            .orElse(null);
    }
}
