package org.openadt.core;

import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

final class AdtHttpReentranceTicketFlow implements AdtHttpTicketProvider {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final String CALLBACK_PATH = "/adt/redirect";
    private final Function<String, String> envProvider;
    private final Consumer<URI> browserOpener;

    AdtHttpReentranceTicketFlow() {
        this(System::getenv, AdtHttpReentranceTicketFlow::openInDesktopBrowser);
    }

    AdtHttpReentranceTicketFlow(Function<String, String> envProvider, Consumer<URI> browserOpener) {
        this.envProvider = envProvider;
        this.browserOpener = browserOpener;
    }

    @Override
    public String acquireTicket(OpenAdtConfig config, SystemProfile system) {
        URI frontend = resolveFrontend(system);
        int requestedPort = resolveCallbackPort(config);
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        HttpServer server = createCallbackServer(requestedPort, ticketFuture);
        server.start();
        try {
            int actualPort = server.getAddress().getPort();
            URI callbackUrl = URI.create("http://localhost:" + actualPort + CALLBACK_PATH);
            URI reentranceUrl = buildReentranceTicketUrl(frontend, system, callbackUrl);
            System.err.println("Opening browser SSO flow: " + reentranceUrl);
            browserOpener.accept(reentranceUrl);
            return ticketFuture.get(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to acquire ADT reentrance ticket: " + error.getMessage(), error);
        } finally {
            server.stop(0);
        }
    }

    static URI buildReentranceTicketUrl(URI frontend, SystemProfile system, URI callbackUrl) {
        URI endpoint = originUri(frontend).resolve("/sap/bc/adt/core/http/reentranceticket");
        Map<String, String> parameters = new LinkedHashMap<>();
        if (system != null && system.getClient() != null && !system.getClient().isBlank()) {
            parameters.put("sap-client", system.getClient().trim());
        }
        String language = "EN";
        if (system != null && system.getLanguage() != null && !system.getLanguage().isBlank()) {
            language = system.getLanguage().trim();
        }
        parameters.put("sap-language", language);
        parameters.put("redirect-url", callbackUrl.toString());
        parameters.put("_", Long.toString(System.currentTimeMillis()));

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return URI.create(endpoint + "?" + query);
    }

    private HttpServer createCallbackServer(int requestedPort, CompletableFuture<String> ticketFuture) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 0);
            server.createContext(CALLBACK_PATH, exchange -> {
                try {
                    String ticket = extractQueryParam(exchange.getRequestURI(), "reentrance-ticket");
                    if (ticket != null && !ticket.isBlank()) {
                        ticketFuture.complete(ticket);
                        writeResponse(exchange, 200, "OpenADT ticket received. You can close this tab.");
                    } else {
                        writeResponse(exchange, 400, "Missing reentrance-ticket parameter.");
                    }
                } catch (Exception error) {
                    ticketFuture.completeExceptionally(error);
                    writeResponse(exchange, 500, "OpenADT failed to process callback.");
                }
            });
            return server;
        } catch (IOException error) {
            throw new IllegalStateException("Failed to start local callback endpoint on /adt/redirect: " + error.getMessage(), error);
        }
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private int resolveCallbackPort(OpenAdtConfig config) {
        String rawPort = runtime(config, OpenAdtConfig.RuntimeConfig::getHttpCallbackPort);
        if (rawPort == null || rawPort.isBlank()) {
            rawPort = envProvider.apply("OPENADT_HTTP_CALLBACK_PORT");
        }
        if (rawPort == null || rawPort.isBlank()) {
            return 0;
        }
        try {
            int port = Integer.parseInt(rawPort.trim());
            if (port < 0 || port > 65535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (NumberFormatException error) {
            throw new IllegalStateException("Invalid OPENADT_HTTP_CALLBACK_PORT: " + rawPort);
        }
    }

    private static URI resolveFrontend(SystemProfile system) {
        if (system == null || system.getAdt() == null || system.getAdt().getDiscoveryUrl() == null || system.getAdt().getDiscoveryUrl().isBlank()) {
            throw new IllegalStateException("HTTP ADT transport requires destinations.<alias>.adt.discovery_url for browser SSO.");
        }
        String value = system.getAdt().getDiscoveryUrl();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        return URI.create(value);
    }

    private static URI originUri(URI uri) {
        return URI.create(uri.getScheme() + "://" + uri.getAuthority() + "/");
    }

    private static String extractQueryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            if (!key.equals(name)) {
                continue;
            }
            String value = index >= 0 ? pair.substring(index + 1) : "";
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String runtime(OpenAdtConfig config, Function<OpenAdtConfig.RuntimeConfig, String> getter) {
        if (config == null || config.getRuntime() == null) {
            return null;
        }
        String value = getter.apply(config.getRuntime());
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static void openInDesktopBrowser(URI uri) {
        if (!Desktop.isDesktopSupported()) {
            System.err.println("Desktop browser integration is not available. Open this URL manually: " + uri);
            return;
        }
        try {
            if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
            System.err.println("Desktop browse action is not supported. Open this URL manually: " + uri);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to open browser URL: " + uri, error);
        }
    }
}
