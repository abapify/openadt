package org.openadt.cli;

import org.openadt.core.AdtRestRfcClient;
import org.openadt.core.ConfigLoader;
import org.openadt.core.JCoDestinationFactory;
import org.openadt.core.OpenAdtConfig;
import org.openadt.core.ProxyRequest;
import org.openadt.core.ProxyResponse;
import org.openadt.core.SystemProfile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "fetch",
    mixinStandardHelpOptions = true,
    description = "Fetch an ADT resource from an SAP system"
)
public class FetchCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "ADT URL path (e.g. /sap/bc/adt/programs/programs/MY_PROG)")
    private String path;

    @Parameters(index = "1", description = "System alias", arity = "0..1")
    private String systemAlias;

    @Option(names = {"--method", "-X"}, description = "HTTP method (default: GET)", defaultValue = "GET")
    private String method;

    @Option(names = {"--header", "-H"}, description = "Add header (NAME:VALUE)")
    private Map<String, String> headers = new LinkedHashMap<>();

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

        if (config.getRuntime() == null || config.getRuntime().getJcoJar() == null) {
            System.err.println("JCo jar not configured. Run 'openadt setup' first.");
            return 1;
        }

        JCoDestinationFactory factory = JCoDestinationFactory.fromJarPath(
            Path.of(config.getRuntime().getJcoJar()));
        AdtRestRfcClient rfcClient = new AdtRestRfcClient(factory);

        ProxyRequest request = new ProxyRequest(method, path, "HTTP/1.1", headers, new byte[0]);
        ProxyResponse response = rfcClient.execute(system, request);

        System.out.printf("HTTP/1.1 %d %s%n", response.statusCode(), response.reasonPhrase());
        response.headers().forEach((k, v) -> System.out.printf("%s: %s%n", k, v));
        System.out.println();
        if (response.body() != null && response.body().length > 0) {
            System.out.print(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        }

        return 0;
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
