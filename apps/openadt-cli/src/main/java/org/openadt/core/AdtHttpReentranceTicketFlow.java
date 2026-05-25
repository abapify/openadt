package org.openadt.core;

import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.Console;
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

final class AdtHttpReentranceTicketFlow implements AdtHttpTicketProvider {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CALLBACK_GRACE_PERIOD = Duration.ofSeconds(30);
    private static final Duration DEFAULT_BRIDGE_WAIT = Duration.ofSeconds(15);
    private static final String CALLBACK_PATH = "/adt/redirect";
    private static final String SSO_LAUNCH_PATH = "/adt/open";
    /**
     * Full ADT Atom discovery for browser SSO bridge. Triggers ICF session on the ADT path after
     * corporate landing. {@code /sap/bc/adt/core/discovery} often returns {@code 200} Atom that the
     * browser downloads instead of SAML redirects on a cold login.
     */
    private static final String OPENADT_HTTP_SSO_NON_INTERACTIVE = "OPENADT_HTTP_SSO_NON_INTERACTIVE";
    private static final String OPENADT_HTTP_SSO_SKIP_BRIDGE = "OPENADT_HTTP_SSO_SKIP_BRIDGE";
    /** Opt-in: open ADT discovery bridge tab before reentrance (often unnecessary; default is reentrance-only). */
    private static final String OPENADT_HTTP_SSO_OPEN_BRIDGE = "OPENADT_HTTP_SSO_OPEN_BRIDGE";
    static final String SSO_LAUNCH_PAGE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>OpenADT SSO</title>
              <script>
                (function () {
                  var target = new URLSearchParams(location.search).get('target');
                  if (!target || !/^https?:\\/\\//i.test(target)) {
                    return;
                  }
                  var popup = window.open(target, 'openadt_sso');
                  if (popup) {
                    try { window.close(); } catch (e) {}
                    setTimeout(function () { try { window.close(); } catch (e2) {} }, 150);
                  } else {
                    location.replace(target);
                  }
                })();
              </script>
            </head>
            <body>
              <p>Opening SAP sign-on...</p>
            </body>
            </html>
            """;
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
        String alias = system != null && system.getAlias() != null ? system.getAlias() : "unknown";
        CliLog.error("Browser SSO for " + alias + " — complete sign-in in the browser; keep this terminal open.");
        CliLog.diagnostic("Browser SSO using destinations." + alias + " discovery_url: " + frontend);
        int requestedPort = resolveCallbackPort(config);
        SsoStepPlan ssoSteps = openPreReentranceBrowserSteps(frontend, system);

        String csrfState = UUID.randomUUID().toString();
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        String callbackHost = resolveCallbackHost(config);
        HttpServer server = createCallbackServer(callbackHost, requestedPort, ticketFuture, csrfState);
        server.start();
        URI callbackUrl = null;
        try {
            int actualPort = server.getAddress().getPort();
            callbackUrl = buildCallbackUrl(callbackHost, actualPort, csrfState);
            SsoCallbackRegistry.markActive(callbackUrl, actualPort);
            URI reentranceUrl = buildReentranceTicketUrl(frontend, system, callbackUrl);
            CliLog.error(
                "Waiting for SSO callback on " + AdtHttpPaths.SCHEME_HTTP_PREFIX + callbackHost + ":" + actualPort + CALLBACK_PATH
            );
            CliLog.diagnostic("Local callback URL: " + callbackUrl);
            waitBeforeReentranceStep();
            CliLog.diagnostic(
                "Step "
                    + ssoSteps.reentranceStep()
                    + "/"
                    + ssoSteps.totalSteps()
                    + ": reentrance-ticket: "
                    + reentranceUrl
            );
            browserOpener.accept(buildSsoLaunchUrl(callbackHost, actualPort, reentranceUrl));
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

    static URI buildSsoLaunchUrl(String host, int port, URI reentranceUrl) {
        return URI.create(
            AdtHttpPaths.SCHEME_HTTP_PREFIX + host + ":" + port + SSO_LAUNCH_PATH + "?target=" + urlEncode(reentranceUrl.toString())
        );
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
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
        }
        return URI.create(endpoint + "?" + query);
    }

    /**
     * SAML/Okta is often bound to the frontend origin (/) rather than deep ICF paths such as
     * {@code /sap/bc/adt/core/http/reentranceticket}. Open that landing URL first so the browser
     * establishes SSO cookies before the reentrance-ticket redirect chain runs.
     */
    static URI resolveSsoLandingUrl(SystemProfile system) {
        return resolveSsoLandingUrl(system, key -> null);
    }

    static URI resolveSsoLandingUrl(SystemProfile system, UnaryOperator<String> envProvider) {
        if (isTruthy(envProvider.apply("OPENADT_HTTP_SSO_SKIP_LANDING"))) {
            return null;
        }
        String configured = envProvider.apply("OPENADT_HTTP_SSO_LANDING_URL");
        if (configured == null || configured.isBlank()) {
            configured = system != null && system.getAdt() != null ? system.getAdt().getSsoLandingUrl() : null;
        }
        if (configured != null && !configured.isBlank()) {
            URI landing = URI.create(configured.trim());
            assertLiveBrowserUrl(landing, system, "sso_landing_url");
            return landing;
        }
        // Never default to frontend / — warm portal sessions open Fiori (/fiori#Shell-home)
        // without ADT ICF cookies. Set sso_landing_url to your IdP/Okta app URL when needed.
        return null;
    }

    /**
     * Browser URL that establishes the ADT ICF session before reentrance-ticket.
     * When {@code discovery_url} ends at {@code /sap/bc/adt}, map to {@code /sap/bc/adt/discovery}
     * so the browser does not show ExceptionResourceNotFound for the bare collection path.
     */
    static URI resolveSsoBridgeUrl(URI frontend) {
        if (frontend == null) {
            return null;
        }
        String path = frontend.getRawPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return null;
        }
        if (isBareAdtCollectionPath(path)) {
            return originUri(frontend).resolve(AdtHttpPaths.ADT_DISCOVERY);
        }
        return frontend;
    }

    private static boolean isBareAdtCollectionPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return AdtHttpPaths.ADT_ICF_ROOT.equalsIgnoreCase(normalized);
    }

    private record SsoStepPlan(int reentranceStep, int totalSteps) {
    }

    private SsoStepPlan openPreReentranceBrowserSteps(URI frontend, SystemProfile system) {
        URI landingUrl = resolveSsoLandingUrl(system, envProvider);
        URI bridgeUrl = resolveSsoBridgeUrl(frontend);
        Console console = System.console();
        boolean interactive = console != null && !isTruthy(envProvider.apply(OPENADT_HTTP_SSO_NON_INTERACTIVE));
        int step = 1;
        boolean openBridge = bridgeUrl != null && shouldOpenBridgeInBrowser(interactive);
        int totalSteps = (landingUrl != null ? 1 : 0) + (openBridge ? 1 : 0) + 1;

        if (landingUrl != null) {
            CliLog.diagnostic(
                "Step " + step + "/" + totalSteps
                    + ": optional sso_landing_url (your IdP/Okta entry — not site /): "
                    + landingUrl
            );
            browserOpener.accept(landingUrl);
            waitForSsoStep(
                console,
                interactive,
                "Complete corporate SSO if prompted, then press Enter to continue..."
            );
            step++;
        }

        if (openBridge) {
            CliLog.diagnostic(
                "Step " + step + "/" + totalSteps
                    + ": optional ADT bridge (OPENADT_HTTP_SSO_OPEN_BRIDGE): "
                    + bridgeUrl
            );
            browserOpener.accept(bridgeUrl);
            waitForSsoStep(
                console,
                interactive,
                "When ADT ICF session is ready, press Enter to continue..."
            );
            waitForBridgeInNonInteractiveMode();
            step++;
        } else if (bridgeUrl != null) {
            CliLog.diagnostic(
                "ADT bridge tab skipped (default). SSO runs via reentrance-ticket redirects. "
                    + "Set " + OPENADT_HTTP_SSO_OPEN_BRIDGE + "=1 to open " + bridgeUrl + " first."
            );
        }

        if (bridgeUrl == null && landingUrl == null) {
            throw new IllegalStateException(
                "HTTP browser SSO requires destinations.<alias>.adt.discovery_url with an ADT path such as /sap/bc/adt."
            );
        }
        return new SsoStepPlan(step, totalSteps);
    }

    private void waitForBridgeInNonInteractiveMode() {
        Console console = System.console();
        boolean interactive = console != null && !isTruthy(envProvider.apply(OPENADT_HTTP_SSO_NON_INTERACTIVE));
        if (interactive) {
            return;
        }
        Duration wait = resolveBridgeWait();
        if (wait.isZero() || wait.isNegative()) {
            return;
        }
        CliLog.diagnostic(
            "Non-interactive terminal: waiting "
                + wait.toSeconds()
                + "s for browser SAML on ADT entry before opening reentrance-ticket..."
        );
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for browser SSO on ADT entry", error);
        }
    }

    private boolean shouldOpenBridgeInBrowser(boolean interactive) {
        return shouldOpenBridgeInBrowser(envProvider, interactive);
    }

    static boolean shouldOpenBridgeInBrowser(UnaryOperator<String> envProvider, boolean interactive) {
        if (isTruthy(envProvider.apply(OPENADT_HTTP_SSO_SKIP_BRIDGE))) {
            return false;
        }
        if (!isTruthy(envProvider.apply(OPENADT_HTTP_SSO_OPEN_BRIDGE))) {
            return false;
        }
        if (!interactive) {
            Duration wait = resolveBridgeWait(envProvider);
            if (wait.isZero() || wait.isNegative()) {
                return false;
            }
        }
        return true;
    }

    private Duration resolveBridgeWait() {
        return resolveBridgeWait(envProvider);
    }

    private static Duration resolveBridgeWait(UnaryOperator<String> envProvider) {
        String rawSeconds = envProvider.apply("OPENADT_HTTP_SSO_BRIDGE_WAIT_SECONDS");
        if (rawSeconds != null && !rawSeconds.isBlank()) {
            try {
                long seconds = Long.parseLong(rawSeconds.trim());
                if (seconds >= 0) {
                    return Duration.ofSeconds(seconds);
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_BRIDGE_WAIT;
    }

    private void waitBeforeReentranceStep() {
        Console console = System.console();
        boolean interactive = console != null && !isTruthy(envProvider.apply(OPENADT_HTTP_SSO_NON_INTERACTIVE));
        if (!interactive) {
            return;
        }
        console.readLine(
            "Press Enter to start the localhost callback and open reentrance-ticket (keep this terminal running)... "
        );
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

    private static void waitForSsoStep(Console console, boolean interactive, String prompt) {
        if (!interactive) {
            return;
        }
        console.readLine(prompt + " ");
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

    private HttpServer createCallbackServer(String host, int requestedPort, CompletableFuture<String> ticketFuture, String expectedState) {
        try {
            resolveLoopbackAddress(host);
            int bindPort = sanitizeCallbackBindPort(requestedPort);
            HttpServer server = LoopbackSsoCallbackServerFactory.create(bindPort);
            server.createContext(SSO_LAUNCH_PATH, exchange -> handleSsoLaunchExchange(exchange));
            server.createContext(
                CALLBACK_PATH,
                exchange -> handleCallbackExchange(exchange, ticketFuture, expectedState)
            );
            return server;
        } catch (IOException error) {
            throw new IllegalStateException("Failed to start local callback endpoint on /adt/redirect: " + error.getMessage(), error);
        }
    }

    private static void handleSsoLaunchExchange(com.sun.net.httpserver.HttpExchange exchange) {
        try {
            writeHtmlResponse(exchange, 200, SSO_LAUNCH_PAGE);
        } catch (IOException error) {
            try {
                writeResponse(exchange, 500, "OpenADT failed to start browser SSO.");
            } catch (IOException ignored) {
                // Best-effort error page.
            }
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
        if (system == null || system.getAdt() == null || system.getAdt().getDiscoveryUrl() == null || system.getAdt().getDiscoveryUrl().isBlank()) {
            throw new IllegalStateException("HTTP ADT transport requires destinations.<alias>.adt.discovery_url for browser SSO.");
        }
        String value = system.getAdt().getDiscoveryUrl();
        value = AdtHttpPaths.withHttpsSchemeIfMissing(value);
        URI frontend = URI.create(value);
        assertLiveBrowserUrl(frontend, system, "discovery_url");
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
                + " with your logical ADT frontend (from saprules.xml), not docs/test placeholders."
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
        if (!Desktop.isDesktopSupported()) {
            CliLog.error("Desktop browser integration is not available. Open this URL manually: " + uri);
            return;
        }
        try {
            if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
            CliLog.error("Desktop browse action is not supported. Open this URL manually: " + uri);
        } catch (IOException error) {
            CliLog.error("Failed to open desktop browser automatically (" + error.getMessage()
                + "). Open this URL manually: " + uri);
        }
    }
}
