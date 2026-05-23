package org.openadt.cli;

import org.openadt.core.AdtTransportClient;
import org.openadt.core.AdtTransportFactory;
import org.openadt.core.ConfigLoader;
import org.openadt.core.LocalProxyRegistry;
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
    private static final String DEFAULT_LISTEN = "127.0.0.1:8079";

    @Parameters(index = "0", description = "System alias to proxy", arity = "0..1")
    private String systemAlias;

    @Option(names = {"--listen"}, description = "Bind address:port (default: config proxy.listen or 127.0.0.1:8079)")
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

        String effectiveListen = resolveListen(config);

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

        AdtTransportClient transportClient;
        try {
            transportClient = AdtTransportFactory.create(config, system);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        LocalAdtProxyServer proxyServer = new LocalAdtProxyServer(transportClient);
        int port = proxyServer.start(system, effectiveListen, effectiveAuth, effectiveUsername, effectivePassword);
        String host = parseHost(effectiveListen);
        String bound = host + ":" + port;

        LocalProxyRegistry.register(new LocalProxyRegistry.ProxyEndpoint(
            system.getAlias(),
            host,
            port,
            "basic".equalsIgnoreCase(effectiveAuth),
            effectiveUsername
        ));

        System.out.printf("OpenADT proxy for system '%s' listening on %s%n", system.getAlias(), bound);
        System.out.println("Keep this running; openadt fetch reuses it automatically.");
        System.out.println("Press Ctrl+C to stop.");
        Object lock = new Object();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LocalProxyRegistry.unregister(system.getAlias());
            } catch (Exception ignored) {
                // best effort
            }
            proxyServer.stop();
            synchronized (lock) { lock.notifyAll(); }
        }));
        synchronized (lock) {
            try { lock.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        return 0;
    }

    private String resolveListen(OpenAdtConfig config) {
        if (listen != null && !listen.isBlank()) {
            return listen;
        }
        if (config.getProxy() != null && config.getProxy().getListen() != null
            && !config.getProxy().getListen().isBlank()) {
            return config.getProxy().getListen();
        }
        return DEFAULT_LISTEN;
    }

    static String parseHost(String listenAddress) {
        if (listenAddress == null || listenAddress.isBlank()) {
            return "127.0.0.1";
        }
        int lastColon = listenAddress.lastIndexOf(':');
        if (lastColon < 0) {
            return listenAddress;
        }
        return listenAddress.substring(0, lastColon);
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
