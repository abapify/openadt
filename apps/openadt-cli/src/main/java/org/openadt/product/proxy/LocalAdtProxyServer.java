package org.openadt.product.proxy;

import com.sun.net.httpserver.HttpServer;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.config.SystemProfile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class LocalAdtProxyServer {
    private static final String HEALTH_PATH = "/__openadt/health";

    private final AdtTransportClient transportClient;
    private HttpServer server;

    public LocalAdtProxyServer(AdtTransportClient transportClient) {
        this.transportClient = transportClient;
    }

    /**
     * Start the proxy server.
     *
     * @param system        the SAP system to proxy
     * @param listenAddress bind address in {@code host:port} format (port 0 = OS-assigned)
     * @param authType      local auth type ("basic" or null/empty for none)
     * @param username      local proxy username (used when authType is "basic")
     * @param password      local proxy password (used when authType is "basic");
     *                      never forwarded to SAP
     * @return the actual port the server is listening on
     */
    public int start(SystemProfile system, String listenAddress, String authType,
                     String username, String password) throws IOException {
        InetSocketAddress bindAddress = parseListenAddress(listenAddress);
        server = HttpServer.create(bindAddress, 0);
        server.createContext(HEALTH_PATH, exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        AdtProxyHandler handler = new AdtProxyHandler(system, transportClient);

        var context = server.createContext("/", handler);

        if ("basic".equalsIgnoreCase(authType)) {
            String effectiveUsername = username != null ? username : "openadt";
            if (password == null || password.isBlank()) {
                throw new IllegalStateException(
                    "A password is required when local auth is set to 'basic'");
            }
            context.getFilters().add(new ProxyAuthFilter(effectiveUsername, password));
        }

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return server != null ? server.getAddress().getPort() : -1;
    }

    private InetSocketAddress parseListenAddress(String listenAddress) {
        if (listenAddress == null || listenAddress.isBlank()) {
            return new InetSocketAddress("127.0.0.1", 0);
        }
        int lastColon = listenAddress.lastIndexOf(':');
        if (lastColon < 0) {
            return new InetSocketAddress(listenAddress, 0);
        }
        String host = listenAddress.substring(0, lastColon);
        int port = Integer.parseInt(listenAddress.substring(lastColon + 1));
        return new InetSocketAddress(host, port);
    }
}
