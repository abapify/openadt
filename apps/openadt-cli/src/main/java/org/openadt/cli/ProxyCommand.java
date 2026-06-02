package org.openadt.cli;

import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.AdtTransportFactory;
import org.openadt.config.CliLog;
import org.openadt.config.ConfigLoader;
import org.openadt.config.DestinationProfileResolver;
import org.openadt.config.SessionContext;
import org.openadt.product.proxy.LocalProxyRegistry;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.product.proxy.LocalAdtProxyServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.io.IOException;
@Command(
    name = "proxy",
    mixinStandardHelpOptions = true,
    description = "Start the local ADT proxy server"
)
public class ProxyCommand implements Callable<Integer> {
    private static final String DEFAULT_LISTEN = "127.0.0.1:8079";

    @Parameters(index = "0", description = "System alias (default: active session from auth login)", arity = "0..1")
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

    @Option(names = {"--profile"}, description = "Authentication profile name (e.g. snc, sso)")
    private String profile;

    @Override
    public Integer call() throws Exception {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        OpenAdtConfig config = loader.load(effectivePath);

        String effectiveAlias = resolveEffectiveAlias(config);
        SystemProfile system = resolveSystem(config, effectiveAlias);
        if (system == null) {
            return 1;
        }

        String effectiveListen = resolveListen(config);
        ProxyAuthSettings authSettings = resolveAuthSettings(config);
        if (!validateBasicAuth(authSettings)) {
            return 1;
        }

        AdtTransportClient transportClient = createTransportClient(config, system);
        if (transportClient == null) {
            return 1;
        }

        return runUntilShutdown(system, effectiveListen, authSettings, transportClient);
    }

    private String resolveEffectiveAlias(OpenAdtConfig config) {
        try {
            return SessionContext.requireAlias(config, systemAlias);
        } catch (IllegalArgumentException e) {
            CliLog.error(e.getMessage());
            return null;
        }
    }

    private SystemProfile resolveSystem(OpenAdtConfig config, String effectiveAlias) {
        try {
            return DestinationProfileResolver.resolve(config, effectiveAlias, profile);
        } catch (IllegalArgumentException e) {
            CliLog.error(e.getMessage());
            return null;
        }
    }

    private ProxyAuthSettings resolveAuthSettings(OpenAdtConfig config) {
        String effectiveAuth = localAuth;
        if (effectiveAuth == null && config.getProxy() != null) {
            effectiveAuth = config.getProxy().getAuth();
        }
        String effectiveUsername = localUsername;
        if (effectiveUsername == null && config.getProxy() != null && config.getProxy().getUsername() != null) {
            effectiveUsername = config.getProxy().getUsername();
        }
        if (effectiveUsername == null) {
            effectiveUsername = "openadt";
        }
        String effectivePassword = localPassword != null ? localPassword : System.getenv("OPENADT_PROXY_PASSWORD");
        return new ProxyAuthSettings(effectiveAuth, effectiveUsername, effectivePassword);
    }

    private boolean validateBasicAuth(ProxyAuthSettings authSettings) {
        if (!"basic".equalsIgnoreCase(authSettings.auth())) {
            return true;
        }
        if (authSettings.password() != null && !authSettings.password().isBlank()) {
            return true;
        }
        CliLog.error("Local proxy password required for basic auth. " +
            "Use --local-password or set OPENADT_PROXY_PASSWORD environment variable.");
        return false;
    }

    private AdtTransportClient createTransportClient(OpenAdtConfig config, SystemProfile system) throws Exception {
        try {
            return AdtTransportFactory.create(config, system);
        } catch (IllegalStateException e) {
            CliLog.error(e.getMessage());
            return null;
        }
    }

    private int runUntilShutdown(
        SystemProfile system,
        String effectiveListen,
        ProxyAuthSettings authSettings,
        AdtTransportClient transportClient
    ) throws IOException {
        LocalAdtProxyServer proxyServer = new LocalAdtProxyServer(transportClient);
        int port = proxyServer.start(
            system,
            effectiveListen,
            authSettings.auth(),
            authSettings.username(),
            authSettings.password()
        );
        String host = parseHost(effectiveListen);
        String bound = host + ":" + port;

        LocalProxyRegistry.register(new LocalProxyRegistry.ProxyEndpoint(
            system.getAlias(),
            profile,
            host,
            port,
            "basic".equalsIgnoreCase(authSettings.auth()),
            authSettings.username()
        ));

        CliLog.info("OpenADT proxy for system '%s'%s listening on %s%n",
            system.getAlias(),
            formatProfileSuffix(profile),
            bound);
        CliLog.info("Keep this running; openadt fetch reuses it automatically.");
        CliLog.info("Press Ctrl+C to stop.");
        CountDownLatch keepRunning = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                LocalProxyRegistry.unregister(system.getAlias(), profile);
            } catch (Exception ignored) {
                // best effort
            }
            proxyServer.stop();
            keepRunning.countDown();
        }));
        try {
            keepRunning.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return 0;
    }

    private static String formatProfileSuffix(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return "";
        }
        return " (profile " + profileName + ")";
    }

    private record ProxyAuthSettings(String auth, String username, String password) {
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
}
