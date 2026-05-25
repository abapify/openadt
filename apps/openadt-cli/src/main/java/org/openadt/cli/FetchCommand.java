package org.openadt.cli;

import org.openadt.core.AdtAcceptHeaders;
import org.openadt.core.AdtTransportClient;
import org.openadt.core.CliLog;
import org.openadt.core.ConfigLoader;
import org.openadt.core.DestinationProfileResolver;
import org.openadt.core.FetchTransportResolver;
import org.openadt.core.LocalProxyRegistry;
import org.openadt.core.ProfileFetchHints;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.ProxyRequest;
import org.openadt.core.ProxyResponse;
import org.openadt.core.ResponseBodyFormatter;
import org.openadt.core.SystemProfile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "fetch",
    mixinStandardHelpOptions = true,
    description = "Fetch an ADT resource from an SAP system"
)
public class FetchCommand implements Callable<Integer> {
    private static final String HEADER_ACCEPT = "Accept";

    @Parameters(index = "0", arity = "0..1", description = "System alias")
    private String systemAlias;

    @Parameters(index = "1", arity = "0..1", description = "ADT URL or path (e.g. /sap/bc/adt/discovery)")
    private String urlOrPath;

    @Option(names = {"--method", "-X"}, description = "HTTP method (default: GET)", defaultValue = "GET")
    private String method;

    @Option(names = {"--header", "-H"}, description = "Add header (\"Name: Value\"), repeatable")
    private List<String> headers = new ArrayList<>();

    @Option(names = {"--accept", "-A"}, description = "Accept header value (repeatable for multiple types)")
    private List<String> accept = new ArrayList<>();

    @Option(names = {"--body", "-d"}, description = "Request body text or @file path")
    private String body;

    @Option(names = {"--output", "-o"}, description = "Write response body to file")
    private Path output;

    @Option(names = {"--include", "-i"}, description = "Include response status and headers in output")
    private boolean include;

    @Option(names = {"--fail", "-f"}, description = "Exit nonzero for HTTP status >= 400")
    private boolean fail;

    @Option(names = {"--pretty"}, description = "Pretty-print JSON or XML response body")
    private boolean pretty;

    @Option(names = {"--raw"}, description = "Body only on stdout; no proxy/tip messages on stderr")
    private boolean raw;

    @Option(names = {"--config", "-c"}, description = "Config file path")
    private Path configPath;

    @Option(names = {"--direct"}, description = "Call SAP via SDK/JCo even when a local openadt proxy is running")
    private boolean direct;

    @Option(names = {"--base-url"}, description = "HTTP ADT frontend URL for direct browser SSO mode (e.g. https://example.sap.invalid)")
    private String baseUrl;

    @Option(names = {"--path"}, description = "ADT path for direct browser SSO mode (e.g. /sap/bc/adt/core/http/systeminformation)")
    private String explicitPath;

    @Option(names = {"--client"}, description = "SAP client for direct browser SSO mode")
    private String explicitClient;

    @Option(names = {"--language"}, description = "SAP language for direct browser SSO mode", defaultValue = "EN")
    private String explicitLanguage;

    @Option(names = {"--ca-cert"}, description = "CA certificate file for HTTPS ADT trust (PEM/DER)")
    private String httpCaCert;

    @Option(names = {"--truststore"}, description = "Truststore file for HTTPS ADT trust")
    private String httpTruststore;

    @Option(names = {"--truststore-password"}, description = "Truststore password for HTTPS ADT trust")
    private String httpTruststorePassword;

    @Option(names = {"--callback-port"}, description = "Local callback port for browser reentrance-ticket flow (0 = random)", defaultValue = "0")
    private int callbackPort;

    @Option(names = {"--profile"}, description = "Authentication profile name (e.g. snc, sso)")
    private String profile;

    @Option(
        names = {"--no-cache"},
        description = "For HTTP SSO: skip reading and writing ~/.openadt/cache/http-sso/ for this fetch only"
    )
    boolean noCache;

    @Override
    public Integer call() throws Exception {
        if (!isDirectHttpMode()) {
            profile = ProfileFetchHints.resolveEffectiveProfile(profile);
        }
        if (isDirectHttpMode() && profile != null && !profile.isBlank()) {
            CliLog.error("--profile cannot be used with --base-url direct HTTP mode.");
            return 1;
        }

        FetchInputs inputs = resolveInputs();
        if (inputs == null) {
            return 1;
        }

        Map<String, String> headerMap = parseHeaders(this.headers);
        applyAcceptHeaders(headerMap, inputs.adtPath());
        byte[] requestBody = resolveBody(body);

        logProxyHints(inputs.system());

        AdtTransportClient transportClient = resolveTransportClient(inputs.config(), inputs.system());
        if (transportClient == null) {
            return 1;
        }

        return executeFetch(inputs.system(), inputs.adtPath(), transportClient, headerMap, requestBody);
    }

    private FetchInputs resolveInputs() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        if (isDirectHttpMode()) {
            return resolveDirectHttpInputs();
        }
        if (systemAlias == null || systemAlias.isBlank() || urlOrPath == null || urlOrPath.isBlank()) {
            CliLog.error("Usage: openadt fetch <SYSTEM> <URL-OR-PATH> (or use --base-url with --path)");
            return null;
        }
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        OpenAdtConfig config = loader.load(effectivePath);
        applyProfileCallbackPort(config, systemAlias);
        SystemProfile destination;
        try {
            destination = DestinationProfileResolver.resolve(config, systemAlias, profile);
        } catch (IllegalArgumentException e) {
            CliLog.error(e.getMessage());
            return null;
        }
        applyHttpTlsCliOverrides(destination);
        return new FetchInputs(config, destination, resolveAdtPath(urlOrPath));
    }

    private FetchInputs resolveDirectHttpInputs() {
        if (explicitClient == null || explicitClient.isBlank()) {
            CliLog.error("Missing SAP client. Use --client with --base-url.");
            return null;
        }
        String rawPath = explicitPath != null ? explicitPath : urlOrPath;
        if (rawPath == null || rawPath.isBlank()) {
            CliLog.error("Missing ADT path. Use --path with --base-url.");
            return null;
        }
        return new FetchInputs(explicitHttpConfig(), explicitHttpSystem(), resolveAdtPath(rawPath));
    }

    private void logProxyHints(SystemProfile system) {
        if (raw || isDirectHttpMode()) {
            return;
        }
        if (!direct && LocalProxyRegistry.findActive(system.getAlias(), profile).isPresent()) {
            CliLog.diagnostic("Using local openadt proxy for " + system.getAlias()
                + (profile != null && !profile.isBlank() ? " (profile " + profile + ")" : ""));
            return;
        }
        if (!direct) {
            String profileHint = profile != null && !profile.isBlank()
                ? " --profile=" + profile
                : "";
            CliLog.diagnostic("Tip: run 'openadt proxy " + system.getAlias()
                + profileHint + "' in another terminal; fetch will reuse it and start faster.");
        }
    }

    private AdtTransportClient resolveTransportClient(OpenAdtConfig config, SystemProfile destination) {
        try {
            return FetchTransportResolver.resolve(config, destination, direct, profile, noCache);
        } catch (Exception e) {
            SystemProfile hintSource = findConfiguredDestination(config, destination.getAlias());
            CliLog.error(ProfileFetchHints.formatTransportError(
                hintSource != null ? hintSource : destination,
                profile,
                e
            ));
            return null;
        }
    }

    private static SystemProfile findConfiguredDestination(OpenAdtConfig config, String alias) {
        if (config.getSystems() == null || alias == null || alias.isBlank()) {
            return null;
        }
        return config.getSystems().stream()
            .filter(system -> alias.equals(system.getAlias()))
            .findFirst()
            .orElse(null);
    }

    private int executeFetch(
        SystemProfile system,
        String adtPath,
        AdtTransportClient transportClient,
        Map<String, String> headerMap,
        byte[] requestBody
    ) throws IOException {
        ProxyRequest request = new ProxyRequest(method, adtPath, "HTTP/1.1", headerMap, requestBody);
        ProxyResponse response;
        try {
            response = transportClient.execute(system, request);
        } catch (Exception error) {
            String detail = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            String alias = effectiveAlias(system);
            CliLog.error("Fetch failed for " + alias + " " + adtPath + ": " + detail);
            if (CliLog.verbose()) {
                error.printStackTrace(CliLog.stderr());
            }
            return 1;
        }

        writeOutput(response);

        if (fail && response.statusCode() >= 400) {
            return 1;
        }
        return 0;
    }

    private record FetchInputs(OpenAdtConfig config, SystemProfile system, String adtPath) {
    }

    private String resolveAdtPath(String urlOrPath) {
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            URI uri = URI.create(urlOrPath);
            String pathAndQuery = uri.getRawPath();
            if (uri.getRawQuery() != null) {
                pathAndQuery += "?" + uri.getRawQuery();
            }
            return pathAndQuery;
        }
        return urlOrPath;
    }

    private Map<String, String> parseHeaders(List<String> headerList) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String header : headerList) {
            int colon = header.indexOf(':');
            if (colon > 0) {
                map.put(header.substring(0, colon).trim(), header.substring(colon + 1).trim());
            }
        }
        return map;
    }

    private void applyAcceptHeaders(Map<String, String> headerMap, String adtPath) {
        if (!accept.isEmpty() && !hasHeader(headerMap, HEADER_ACCEPT)) {
            headerMap.put(HEADER_ACCEPT, String.join(", ", accept));
        }
        if (!hasHeader(headerMap, HEADER_ACCEPT)) {
            headerMap.put(HEADER_ACCEPT, AdtAcceptHeaders.defaultAcceptHeaderValue(adtPath));
        }
    }

    private static boolean hasHeader(Map<String, String> headerMap, String name) {
        for (String key : headerMap.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private byte[] resolveBody(String bodyArg) throws IOException {
        if (bodyArg == null) return new byte[0];
        if (bodyArg.startsWith("@")) {
            return Files.readAllBytes(Path.of(bodyArg.substring(1)));
        }
        return bodyArg.getBytes(StandardCharsets.UTF_8);
    }

    private void writeOutput(ProxyResponse response) throws IOException {
        byte[] responseBody = response.body() != null ? response.body() : new byte[0];
        byte[] outBody = pretty
            ? ResponseBodyFormatter.format(response.headers(), responseBody)
            : responseBody;

        if (output != null) {
            if (include && !raw) {
                printStatusAndHeaders(response, CliLog.stdout());
            }
            Files.write(output, outBody);
            return;
        }

        if (include && !raw) {
            printStatusAndHeaders(response, CliLog.stdout());
        }
        if (outBody.length > 0) {
            CliLog.stdout().write(outBody);
            CliLog.stdout().flush();
        }
    }

    private void printStatusAndHeaders(ProxyResponse response, OutputStream out) throws IOException {
        String statusLine = String.format("%s %d %s%n", response.version(), response.statusCode(), response.reasonPhrase());
        out.write(statusLine.getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, String> entry : response.headers().entrySet()) {
            String headerLine = String.format("%s: %s%n", entry.getKey(), entry.getValue());
            out.write(headerLine.getBytes(StandardCharsets.UTF_8));
        }
        out.write(System.lineSeparator().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void applyProfileCallbackPort(OpenAdtConfig config, String alias) {
        OpenAdtConfig.RuntimeConfig runtime = config.getRuntime();
        if (runtime == null) {
            runtime = new OpenAdtConfig.RuntimeConfig();
            config.setRuntime(runtime);
        }
        if (callbackPort != 0) {
            runtime.setHttpCallbackPort(Integer.toString(callbackPort));
            return;
        }
        String profileCallbackPort = DestinationProfileResolver.resolveProfileCallbackPort(config, alias, profile);
        if (profileCallbackPort == null || profileCallbackPort.isBlank()) {
            return;
        }
        runtime.setHttpCallbackPort(profileCallbackPort);
    }

    private boolean isDirectHttpMode() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    private OpenAdtConfig explicitHttpConfig() {
        OpenAdtConfig config = new OpenAdtConfig();
        config.setVersion(1);
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setHttpCaCert(httpCaCert);
        runtime.setHttpTruststore(httpTruststore);
        runtime.setHttpTruststorePassword(httpTruststorePassword);
        runtime.setHttpCallbackPort(Integer.toString(callbackPort));
        config.setRuntime(runtime);
        return config;
    }

    private void applyHttpTlsCliOverrides(SystemProfile system) {
        if ((httpCaCert == null || httpCaCert.isBlank())
            && (httpTruststore == null || httpTruststore.isBlank())
            && (httpTruststorePassword == null || httpTruststorePassword.isBlank())) {
            return;
        }
        SystemProfile.AdtConfig adt = system.getAdt();
        if (adt == null) {
            adt = new SystemProfile.AdtConfig();
            system.setAdt(adt);
        }
        if (httpCaCert != null && !httpCaCert.isBlank()) {
            adt.setHttpCaCert(httpCaCert);
        }
        if (httpTruststore != null && !httpTruststore.isBlank()) {
            adt.setHttpTruststore(httpTruststore);
        }
        if (httpTruststorePassword != null && !httpTruststorePassword.isBlank()) {
            adt.setHttpTruststorePassword(httpTruststorePassword);
        }
    }

    private SystemProfile explicitHttpSystem() {
        SystemProfile system = new SystemProfile();
        system.setAlias("HTTP");
        system.setClient(explicitClient);
        system.setLanguage(explicitLanguage);
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setTransport("http");
        adt.setDiscoveryUrl(baseUrl);
        system.setAdt(adt);
        return system;
    }

    private String effectiveAlias(SystemProfile system) {
        if (system == null || system.getAlias() == null || system.getAlias().isBlank()) {
            return "direct-http";
        }
        return system.getAlias();
    }
}
