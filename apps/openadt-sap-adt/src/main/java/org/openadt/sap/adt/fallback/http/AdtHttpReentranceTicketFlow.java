package org.openadt.sap.adt.fallback.http;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.openadt.config.AdtHttpFrontendUrls;
import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
final class AdtHttpReentranceTicketFlow implements AdtHttpTicketProvider {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CALLBACK_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final String CALLBACK_PATH = "/adt/redirect";
    static final String TICKET_RECEIVED_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>OpenADT</title>
              <script>
                function closeTab() {
                  try { window.open('', '_self'); } catch (e) {}
                  window.close();
                }
                addEventListener('load', function () {
                  closeTab();
                  setTimeout(closeTab, 150);
                });
              </script>
            </head>
            <body>
              <p id="msg">OpenADT ticket received.</p>
              <script>
                setTimeout(function () {
                  var msg = document.getElementById('msg');
                  if (msg) {
                    msg.textContent = 'OpenADT ticket received. You can close this tab.';
                  }
                }, 500);
              </script>
            </body>
            </html>
            """;
    private final UnaryOperator<String> envProvider;
    private final Consumer<URI> browserOpener;

    AdtHttpReentranceTicketFlow() {
        this(System::getenv, AdtHttpReentranceTicketFlow::openInDesktopBrowser);
    }

    AdtHttpReentranceTicketFlow(UnaryOperator<String> envProvider, Consumer<URI> browserOpener) {
        this.envProvider = envProvider;
        this.browserOpener = browserOpener;
    }

    @Override
    public String acquireTicket(OpenAdtConfig config, SystemProfile system) {
        URI frontend = resolveFrontend(system);
        URI browserEntry = resolveBrowserEntryUrl(system, envProvider);
        String alias = system != null && system.getAlias() != null ? system.getAlias() : "unknown";

        String csrfState = UUID.randomUUID().toString();
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        String callbackHost = resolveCallbackHost(config);
        int requestedPort = resolveCallbackPort(config);
        HttpServer server = createCallbackServer(callbackHost, requestedPort, ticketFuture, csrfState);
        server.start();
        URI callbackUrl = null;
        try {
            int actualPort = server.getAddress().getPort();
            callbackUrl = buildCallbackUrl(callbackHost, actualPort, csrfState);
            SsoCallbackRegistry.markActive(callbackUrl, actualPort);
            URI reentranceUrl = buildBrowserSsoEntryUrl(frontend, callbackUrl, system);
            openBrowserEntryIfConfigured(alias, browserEntry, reentranceUrl);
            CliLog.error("Browser SSO for " + alias + ": sign in at " + reentranceUrl);
            CliLog.error(
                "Waiting for redirect to "
                    + AdtHttpPaths.SCHEME_HTTP_PREFIX
                    + callbackHost
                    + ":"
                    + actualPort
                    + CALLBACK_PATH
                    + " (keep this terminal open)"
            );
            CliLog.diagnostic("ADT API base for fetch: " + frontend);
            browserOpener.accept(reentranceUrl);
            return ticketFuture.get(resolveCallbackTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(buildAcquireFailureMessage(callbackUrl, interrupted), interrupted);
        } catch (Exception error) {
            throw new IllegalStateException(buildAcquireFailureMessage(callbackUrl, error), error);
        } finally {
            SsoCallbackRegistry.clear();
            server.stop(resolveCallbackStopDelay(ticketFuture));
        }
    }

    private static int resolveCallbackStopDelay(CompletableFuture<String> ticketFuture) {
        if (ticketFuture.isDone() && !ticketFuture.isCompletedExceptionally()) {
            return (int) CALLBACK_GRACE_PERIOD.toSeconds();
        }
        return 0;
    }

    private String buildAcquireFailureMessage(URI callbackUrl, Exception error) {
        StringBuilder message = new StringBuilder("Failed to acquire ADT reentrance ticket: ");
        message.append(error.getMessage());
        if (callbackUrl != null) {
            message.append(". Expected browser redirect to ").append(callbackUrl);
            message.append(". ").append(SsoCallbackRegistry.stalePortHint(callbackUrl.getPort()));
        }
        message.append(
            " If the browser already shows reentrance-ticket=... in the address bar, "
                + "copy the ticket value into OPENADT_MYSAPSSO2 and retry without browser SSO."
        );
        return message.toString();
    }

    static URI buildCallbackUrl(String host, int port, String csrfState) {
        return URI.create(AdtHttpPaths.SCHEME_HTTP_PREFIX + host + ":" + port + CALLBACK_PATH + "?state=" + csrfState);
    }

    /**
     * ADT reentrance-ticket URL on the configured SAP frontend with {@code redirect-url} to
     * localhost.
     */
    static URI buildBrowserSsoEntryUrl(URI frontend, URI callbackUrl, SystemProfile system) {
        return appendQuery(reentranceTicketEndpoint(frontend), browserSsoParameters(system, callbackUrl));
    }

    private static URI reentranceTicketEndpoint(URI frontend) {
        return originUri(frontend).resolve("/sap/bc/adt/core/http/reentranceticket");
    }

    private static Map<String, String> browserSsoParameters(SystemProfile system, URI callbackUrl) {
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
        return parameters;
    }

    /**
     * Optional manual browser prep URL for SSO/session setup before the ADT reentrance-ticket
     * step.
     */
    static URI resolveBrowserEntryUrl(SystemProfile system) {
        return resolveBrowserEntryUrl(system, key -> null);
    }

    static URI resolveBrowserEntryUrl(SystemProfile system, UnaryOperator<String> envProvider) {
        String configured = envProvider.apply("OPENADT_HTTP_BROWSER_ENTRY_URL");
        if (configured != null && !configured.isBlank()) {
            URI entry = URI.create(configured.trim());
            assertLiveBrowserUrl(entry, system, "OPENADT_HTTP_BROWSER_ENTRY_URL");
            return entry;
        }
        String legacy = envProvider.apply("OPENADT_HTTP_BASE_URL");
        if (legacy != null && !legacy.isBlank()) {
            URI entry = URI.create(legacy.trim());
            assertLiveBrowserUrl(entry, system, "OPENADT_HTTP_BASE_URL");
            return entry;
        }
        URI fromConfig = resolveConfiguredBrowserEntryUrl(system);
        if (fromConfig != null) {
            assertLiveBrowserUrl(fromConfig, system, "browser_entry_url");
        }
        return fromConfig;
    }

    private static URI resolveConfiguredBrowserEntryUrl(SystemProfile system) {
        if (system == null || system.getAdt() == null) {
            return null;
        }
        String raw = system.getAdt().getBrowserEntryUrl();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return URI.create(raw.trim());
    }

    private static URI appendQuery(URI base, Map<String, String> parameters) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        String raw = base.toString();
        String separator = raw.contains("?") ? "&" : "?";
        return URI.create(raw + separator + query);
    }

    private Duration resolveCallbackTimeout() {
        String rawMinutes = envProvider.apply("OPENADT_HTTP_CALLBACK_TIMEOUT_MINUTES");
        if (rawMinutes != null && !rawMinutes.isBlank()) {
            try {
                long minutes = Long.parseLong(rawMinutes.trim());
                if (minutes > 0) {
                    return Duration.ofMinutes(minutes);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_TIMEOUT;
    }

    private static boolean isTruthy(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            default -> false;
        };
    }

    private void openBrowserEntryIfConfigured(String alias, URI browserEntry, URI reentranceUrl) {
        if (browserEntry == null) {
            return;
        }
        CliLog.error("Browser SSO for " + alias + ": open " + browserEntry);
        CliLog.error(
            "Complete sign-in there first. OpenADT cannot verify that browser authorization is ready yet."
        );
        CliLog.error(
            "After the browser is ready, press Enter and OpenADT will continue with the ADT reentrance-ticket URL."
        );
        browserOpener.accept(browserEntry);
        waitForBrowserEntryConfirmation();
        CliLog.diagnostic("Continuing to ADT reentrance-ticket URL: " + reentranceUrl);
    }

    private void waitForBrowserEntryConfirmation() {
        if (isTruthy(envProvider.apply("OPENADT_HTTP_SSO_NON_INTERACTIVE"))) {
            CliLog.error("Continuing without explicit confirmation because OPENADT_HTTP_SSO_NON_INTERACTIVE is enabled.");
            return;
        }
        if (System.console() != null) {
            System.console().readLine("Press Enter to continue to ADT reentrance-ticket...");
            return;
        }
        throw new IllegalStateException(
            "Browser entry confirmation is required before continuing to ADT reentrance-ticket, "
                + "but no interactive console is available. Re-run in an interactive terminal and press Enter after sign-in, "
                + "or set OPENADT_HTTP_SSO_NON_INTERACTIVE=true to allow automatic continuation."
        );
    }

    private HttpServer createCallbackServer(String host, int requestedPort, CompletableFuture<String> ticketFuture, String expectedState) {
        try {
            resolveLoopbackAddress(host);
            int bindPort = sanitizeCallbackBindPort(requestedPort);
            HttpServer server = LoopbackSsoCallbackServerFactory.create(bindPort);
            server.createContext(
                CALLBACK_PATH,
                exchange -> handleCallbackExchange(exchange, ticketFuture, expectedState)
            );
            return server;
        } catch (IOException error) {
            throw new IllegalStateException("Failed to start local callback endpoint on /adt/redirect: " + error.getMessage(), error);
        }
    }

    private void handleCallbackExchange(
        com.sun.net.httpserver.HttpExchange exchange,
        CompletableFuture<String> ticketFuture,
        String expectedState
    ) {
        try {
            if (!validateCallbackState(exchange, ticketFuture, expectedState)) {
                return;
            }
            String ticket = stripMalformedQuerySuffix(extractQueryParam(exchange.getRequestURI(), "reentrance-ticket"));
            if (ticket != null && !ticket.isBlank()) {
                completeTicket(ticketFuture, ticket);
                writeHtmlResponse(exchange, 200, TICKET_RECEIVED_PAGE);
            } else {
                writeResponse(exchange, 400, "Missing reentrance-ticket parameter.");
            }
        } catch (Exception error) {
            failTicket(ticketFuture, error);
            try {
                writeResponse(exchange, 500, "OpenADT failed to process callback.");
            } catch (IOException ignored) {
                // Best-effort error page after callback failure.
            }
        }
    }

    private boolean validateCallbackState(
        com.sun.net.httpserver.HttpExchange exchange,
        CompletableFuture<String> ticketFuture,
        String expectedState
    ) throws IOException {
        String receivedState = stripMalformedQuerySuffix(extractQueryParam(exchange.getRequestURI(), "state"));
        if (receivedState != null && receivedState.equals(expectedState)) {
            return true;
        }
        writeResponse(exchange, 403, "CSRF state mismatch. Possible cross-site request forgery.");
        failTicket(ticketFuture, new SecurityException("CSRF state validation failed"));
        return false;
    }

    /**
     * Some SAP frontends append a cache-buster to {@code redirect-url} with a second {@code ?} instead of
     * {@code &}, corrupting whichever query parameter is last. Strip the bogus suffix from extracted values.
     */
    static String stripMalformedQuerySuffix(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        int extraQuery = rawValue.indexOf('?');
        if (extraQuery >= 0) {
            return rawValue.substring(0, extraQuery);
        }
        return rawValue;
    }

    private static void completeTicket(CompletableFuture<String> ticketFuture, String ticket) {
        if (!ticketFuture.isDone()) {
            ticketFuture.complete(ticket);
        }
    }

    private static void failTicket(CompletableFuture<String> ticketFuture, Exception error) {
        if (!ticketFuture.isDone()) {
            ticketFuture.completeExceptionally(error);
        }
    }

    private static void writeHtmlResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

  /** SAP ADT validates redirect-url and typically accepts {@code localhost}, not {@code 127.0.0.1}. */
    private String resolveCallbackHost(OpenAdtConfig config) {
        String rawHost = runtime(config, OpenAdtConfig.RuntimeConfig::getHttpCallbackHost);
        if (rawHost == null || rawHost.isBlank()) {
            rawHost = envProvider.apply("OPENADT_HTTP_CALLBACK_HOST");
        }
        if (rawHost == null || rawHost.isBlank()) {
            return "localhost";
        }
        String host = rawHost.trim();
        resolveLoopbackAddress(host);
        return host;
    }

    private static InetAddress resolveLoopbackAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!address.isLoopbackAddress()) {
                throw new IllegalArgumentException(
                    "HTTP SSO callback host must resolve to a loopback address (localhost/127.0.0.1/::1), got: "
                        + host
                        + " -> "
                        + address.getHostAddress()
                );
            }
            return address;
        } catch (java.net.UnknownHostException error) {
            throw new IllegalArgumentException("Cannot resolve HTTP SSO callback host: " + host, error);
        }
    }

    private static int sanitizeCallbackBindPort(int requestedPort) {
        if (requestedPort == 0) {
            return 0;
        }
        if (requestedPort < 1024 || requestedPort > 65535) {
            throw new IllegalArgumentException(
                "HTTP SSO callback port must be 0 (random) or 1024-65535, got: " + requestedPort
            );
        }
        return requestedPort;
    }

    /** Starts a loopback callback server for same-package tests without browser SSO. */
    static HttpServer startTestCallbackServer(CompletableFuture<String> ticketFuture, String csrfState) {
        AdtHttpReentranceTicketFlow flow = new AdtHttpReentranceTicketFlow(key -> null, uri -> { });
        HttpServer server = flow.createCallbackServer("localhost", 0, ticketFuture, csrfState);
        server.start();
        return server;
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
        if (system == null || system.getAdt() == null) {
            throw new IllegalStateException("HTTP ADT transport requires destinations.<alias>.adt.base_url for browser SSO.");
        }
        String apiBase = AdtHttpFrontendUrls.resolveAdtApiBase(system.getAdt());
        if (apiBase == null || apiBase.isBlank()) {
            throw new IllegalStateException(
                "HTTP ADT transport requires destinations.<alias>.adt.base_url (SAP frontend origin, e.g. https://host)."
            );
        }
        URI frontend = URI.create(apiBase);
        assertLiveBrowserUrl(frontend, system, "base_url");
        return frontend;
    }

    /**
     * Docs/tests use {@code *.example.com} and {@code *.example.invalid}; reject them before opening a browser.
     */
    static boolean isFictionalExampleHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.trim().toLowerCase();
        return normalized.endsWith(".example.com")
            || normalized.endsWith(".example.invalid")
            || normalized.equals("example.com")
            || normalized.equals("example.invalid");
    }

    private static void assertLiveBrowserUrl(URI url, SystemProfile system, String fieldName) {
        if (url == null || !isFictionalExampleHost(url.getHost())) {
            return;
        }
        String alias = system != null && system.getAlias() != null ? system.getAlias() : "<alias>";
        throw new IllegalStateException(
            "Browser SSO "
                + fieldName
                + " uses fictional fixture host '"
                + url.getHost()
                + "'. Configure destinations."
                + alias
                + ".adt.base_url with your SAP frontend (from saprules.xml), not docs/test placeholders."
        );
    }

    private static URI originUri(URI uri) {
        if (uri.getScheme() == null || uri.getScheme().isBlank()) {
            throw new IllegalStateException("HTTP ADT frontend URL must include scheme (https://): " + uri);
        }
        if (uri.getAuthority() == null || uri.getAuthority().isBlank()) {
            throw new IllegalStateException("HTTP ADT frontend URL must include host[:port]: " + uri);
        }
        return URI.create(uri.getScheme() + "://" + uri.getAuthority() + "/");
    }

    private static String extractQueryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        String matched = null;
        for (String pair : query.split("&")) {
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            if (key.equals(name)) {
                String value = index >= 0 ? pair.substring(index + 1) : "";
                matched = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
                break;
            }
        }
        return matched;
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
        try {
            MfaBrowserLauncher.open(uri.toString());
        } catch (IOException error) {
            CliLog.error("Failed to open desktop browser automatically (" + error.getMessage()
                + "). Open this URL manually: " + uri);
        }
    }
}
