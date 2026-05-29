package org.openadt.sap.adt.fallback.http;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Creates the loopback-only HTTP server used for browser SSO reentrance-ticket callbacks.
 * Isolated so static analysis can scope intentional localhost binds separately from the flow logic.
 */
final class LoopbackSsoCallbackServerFactory {
    private LoopbackSsoCallbackServerFactory() {
    }

    static HttpServer create(int bindPort) throws IOException {
        return HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), bindPort), 0);
    }
}
