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

    @Option(names = {"--listen"}, description = "Bind address:port (default: 127.0.0.1:0)", defaultValue = "127.0.0.1:0")
    private String listen;

    @Option(names = {"--local-auth"}, description = "Local auth type (basic)")
    private String localAuth;

    @Option(names = {"--local-username"}, description = "Local proxy username (default: openadt)")
    private String localUsername;

    @Option(names = {"--local-password"}, description = "Local proxy password (default: OPENADT_PROXY_PASSWORD env var)")
    private String localPassword;

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

        // Resolve effective auth settings: CLI flags override config file
        String effectiveAuth = localAuth != null ? localAuth
            : (config.getProxy() != null ? config.getProxy().getAuth() : null);
        String effectiveUsername = localUsername != null ? localUsername
            : (config.getProxy() != null && config.getProxy().getUsername() != null
                ? config.getProxy().getUsername() : "openadt");
        String effectivePassword = localPassword != null ? localPassword
            : System.getenv("OPENADT_PROXY_PASSWORD");

        if ("basic".equalsIgnoreCase(effectiveAuth) && (effectivePassword == null || effectivePassword.isBlank())) {
            System.err.println("Local proxy password required for basic auth. " +
                "Use --local-password or set OPENADT_PROXY_PASSWORD environment variable.");
            return 1;
        }

        JCoDestinationFactory factory = JCoDestinationFactory.fromJarPath(
            Path.of(config.getRuntime().getJcoJar()));
        AdtRestRfcClient rfcClient = new AdtRestRfcClient(factory);
        LocalAdtProxyServer proxyServer = new LocalAdtProxyServer(rfcClient);
        int port = proxyServer.start(system, listen, effectiveAuth, effectiveUsername, effectivePassword);

        System.out.printf("OpenADT proxy for system '%s' listening on %s%n", system.getAlias(), listen.replace(":0", ":" + port));
        System.out.println("Press Ctrl+C to stop.");
        Object lock = new Object();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            proxyServer.stop();
            synchronized (lock) { lock.notifyAll(); }
        }));
        synchronized (lock) {
            try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

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
