package org.openadt.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.SdkDocumentResult;
import org.openadt.sap.adt.sdk.SdkJsonResult;
import org.openadt.sap.adt.sdk.SdkServiceArgs;
import org.openadt.sap.adt.sdk.SdkServiceResult;
import org.openadt.sap.adt.sdk.AdtSdkServiceGateway;
import org.openadt.sap.adt.services.DiscoveryDocumentSummary;
import picocli.CommandLine.Option;

/**
 * Shared Picocli wiring for registered SDK services ({@link org.openadt.sap.adt.services.SdkServiceRegistry}).
 */
abstract class SdkServiceCommandSupport extends AdtCommandSupport implements Callable<Integer> {
    @Option(names = {"--out", "-o"}, description = "Output file (.json triggers JSON body for document services)")
    Path outFile;

    @Option(names = {"--full"}, description = "Also print full payload to stdout")
    boolean full;

    @Option(names = {"--format"}, description = "Output format: text or json", defaultValue = "text")
    String format;

    @Option(names = {"--json"}, description = "JSON output")
    boolean json;

    @Option(names = {"--param"}, description = "Service parameter key=value (repeatable)", paramLabel = "KEY=VALUE")
    List<String> serviceParams;

    protected abstract String serviceId();

    protected abstract String systemAliasParam();

    protected SdkServiceArgs buildServiceArgs() {
        return SdkServiceArgs.of(parseServiceParams(serviceParams));
    }

    @Override
    public Integer call() throws Exception {
        String systemAlias = systemAliasParam();
        SystemProfile system = null;
        try {
            OpenAdtConfig config = loadConfig();
            system = resolveSystem(config, systemAlias);
            systemAlias = system.getAlias();
            requireSdkTransport(system, "openadt " + serviceId().replace('.', ' '));
            SdkServiceResult result = AdtSdkServiceGateway.invokeService(
                serviceId(),
                config,
                system,
                buildServiceArgs()
            );
            boolean jsonOutput = SdkDocumentOutput.wantsJsonOutput(json, format, outFile)
                || result instanceof SdkJsonResult;
            SdkResultOutput.write(
                result,
                outFile,
                jsonOutput,
                full,
                () -> printTextSummary(result, outFile)
            );
            return result.ok() ? 0 : 1;
        } catch (Exception error) {
            CliLog.error("openadt " + serviceId() + " [" + systemAlias + "]: " + formatTransportError(system, error));
            return 1;
        }
    }

    private void printTextSummary(SdkServiceResult result, Path outFile) {
        if (result instanceof SdkDocumentResult document) {
            CliLog.stdout().println("destination: " + document.destinationId()
                + (document.fromEclipse() ? " (eclipse)" : " (config)"));
            CliLog.stdout().println("http-status: " + document.statusCode());
            CliLog.stdout().println("message: " + document.message());
            if (document.contentType() != null) {
                CliLog.stdout().println("content-type: " + document.contentType());
            }
            CliLog.stdout().println("summary: " + DiscoveryDocumentSummary.summarize(document.body()));
            if (outFile != null) {
                CliLog.stdout().println("file: " + outFile.toAbsolutePath());
            }
            return;
        }
        if (result instanceof SdkJsonResult jsonResult) {
            SdkResultOutput.printTransportListSummary(jsonResult);
        }
    }

    static Map<String, String> parseServiceParams(List<String> params) {
        Map<String, String> map = new LinkedHashMap<>();
        if (params == null) {
            return map;
        }
        for (String param : params) {
            if (param == null || param.isBlank()) {
                continue;
            }
            int eq = param.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Invalid --param (expected key=value): " + param);
            }
            map.put(param.substring(0, eq).trim(), param.substring(eq + 1).trim());
        }
        return map;
    }
}
