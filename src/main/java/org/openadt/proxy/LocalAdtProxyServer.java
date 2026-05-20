package org.openadt.proxy;

import com.sun.net.httpserver.HttpServer;
import org.openadt.core.AdtRestRfcClient;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LocalAdtProxyServer {
    private final OpenAdtConfig config;
    private final AdtRestRfcClient rfcClient;
    private HttpServer server;

    public LocalAdtProxyServer(OpenAdtConfig config, AdtRestRfcClient rfcClient) {
        this.config = config;
        this.rfcClient = rfcClient;
    }

    public void start(SystemProfile system, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        AdtProxyHandler handler = new AdtProxyHandler(system, rfcClient);

        var context = server.createContext("/", handler);

        if (config.getProxy() != null && "basic".equalsIgnoreCase(config.getProxy().getAuth())) {
            String username = config.getProxy().getUsername() != null
                ? config.getProxy().getUsername() : "openadt";
            // Password would come from a secure source in production
            context.getFilters().add(new ProxyAuthFilter(username, ""));
        }

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("OpenADT proxy listening on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
