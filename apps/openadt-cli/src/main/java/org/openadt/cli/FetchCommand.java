package org.openadt.cli;

import org.openadt.core.AdtAcceptHeaders;
import org.openadt.core.AdtTransportClient;
import org.openadt.core.ConfigLoader;
import org.openadt.core.FetchTransportResolver;
import org.openadt.core.LocalProxyRegistry;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.ProxyRequest;
import org.openadt.core.ProxyResponse;
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
    @Parameters(index = "0", description = "System alias")
    private String systemAlias;

    @Parameters(index = "1", description = "ADT URL or path (e.g. /sap/bc/adt/discovery)")
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

    @Option(names = {"--json"}, description = "Pretty-print JSON response body")
    private boolean json;

    @Option(names = {"--raw"}, description = "Write only response body bytes (no headers)")
    private boolean raw;

    @Option(names = {"--config", "-c"}, description = "Config file path")
    private Path configPath;

    @Option(names = {"--direct"}, description = "Call SAP via SDK/JCo even when a local openadt proxy is running")
    private boolean direct;

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

        String adtPath = resolveAdtPath(urlOrPath);
        Map<String, String> headerMap = parseHeaders(this.headers);
        applyAcceptHeaders(headerMap, adtPath);
        byte[] requestBody = resolveBody(body);
        AdtTransportClient transportClient;
        try {
            if (!isQuietOutput()) {
                if (!direct && LocalProxyRegistry.findActive(system.getAlias()).isPresent()) {
                    System.err.println("Using local openadt proxy for " + system.getAlias());
                } else if (!direct) {
                    System.err.println("Tip: run 'openadt proxy " + system.getAlias()
                        + "' in another terminal; fetch will reuse it and start faster.");
                }
            }
            transportClient = FetchTransportResolver.resolve(config, system, direct);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return 1;
        }

        ProxyRequest request = new ProxyRequest(method, adtPath, "HTTP/1.1", headerMap, requestBody);
        ProxyResponse response = transportClient.execute(system, request);

        writeOutput(response);

        if (fail && response.statusCode() >= 400) {
            return 1;
        }
        return 0;
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
        if (!accept.isEmpty() && !hasHeader(headerMap, "Accept")) {
            headerMap.put("Accept", String.join(", ", accept));
        }
        if (!hasHeader(headerMap, "Accept")) {
            headerMap.put("Accept", AdtAcceptHeaders.defaultAcceptHeaderValue(adtPath));
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

    private boolean isQuietOutput() {
        return json || raw;
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

        if (output != null) {
            if (include) {
                printStatusAndHeaders(response, System.out);
            }
            Files.write(output, responseBody);
        } else if (raw) {
            System.out.flush();
            System.out.write(responseBody);
            System.out.flush();
        } else {
            if (include) {
                printStatusAndHeaders(response, System.out);
            }
            if (json && responseBody.length > 0) {
                System.out.println(prettyPrintJson(new String(responseBody, StandardCharsets.UTF_8)));
            } else if (responseBody.length > 0) {
                System.out.write(responseBody);
                System.out.flush();
            }
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

    private String prettyPrintJson(String jsonText) {
        // Simple indentation: insert newlines after { [ , and before } ]
        // For production use, a JSON library would be preferable;
        // this is a best-effort formatter for human-readable output.
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < jsonText.length(); i++) {
            char c = jsonText.charAt(i);
            if (c == '"' && (i == 0 || jsonText.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (inString) {
                sb.append(c);
            } else if (c == '{' || c == '[') {
                sb.append(c);
                sb.append('\n');
                indent++;
                sb.append("  ".repeat(indent));
            } else if (c == '}' || c == ']') {
                sb.append('\n');
                indent--;
                sb.append("  ".repeat(indent));
                sb.append(c);
            } else if (c == ',') {
                sb.append(c);
                sb.append('\n');
                sb.append("  ".repeat(indent));
            } else if (c == ':') {
                sb.append(": ");
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                sb.append(c);
            }
        }
        return sb.toString();
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
