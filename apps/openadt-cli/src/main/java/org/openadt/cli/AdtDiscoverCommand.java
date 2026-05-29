package org.openadt.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtDiscoveryReport;
import org.openadt.sap.adt.sdk.AdtSdkServiceGateway;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "discover",
    mixinStandardHelpOptions = true,
    description = "Run SAP ADT SDK discovery for a configured system"
)
public class AdtDiscoverCommand extends AdtCommandSupport implements Callable<Integer> {
    @Parameters(index = "0", description = "System alias (e.g. DEV)")
    String systemAlias;

    @Option(names = {"--collection"}, description = "ADT discovery collection URI")
    String collection;

    @Option(names = {"--category"}, description = "ADT discovery category term (requires --collection)")
    String category;

    @Option(names = {"--format"}, description = "Output format: text or json", defaultValue = "text")
    String format;

    @Option(names = {"--json"}, description = "JSON output (same as --format json)")
    boolean json;

    @Override
    public Integer call() throws Exception {
        if (category != null && !category.isBlank() && (collection == null || collection.isBlank())) {
            CliLog.error("--category requires --collection");
            return 1;
        }
        OpenAdtConfig config = null;
        SystemProfile system = null;
        try {
            config = loadConfig();
            system = resolveSystem(config, systemAlias);
            requireSdkTransport(system, "openadt adt discover");
            AdtDiscoveryReport report = AdtSdkServiceGateway.discover(config, system, collection, category);
            if (json || "json".equalsIgnoreCase(format)) {
                CliLog.stdout().println(toJson(report));
            } else {
                printText(report);
            }
            return report.ok() ? 0 : 1;
        } catch (Exception error) {
            CliLog.error("openadt adt discover [" + systemAlias + "]: " + formatTransportError(system, error));
            return 1;
        }
    }

    private static void printText(AdtDiscoveryReport report) {
        CliLog.stdout().println("destination: " + report.destinationId()
            + (report.fromEclipse() ? " (eclipse)" : " (config)"));
        CliLog.stdout().println("status: " + (report.ok() ? "OK" : "FAILED"));
        CliLog.stdout().println("message: " + report.statusMessage());
        if (report.memberUri() != null) {
            CliLog.stdout().println("member-uri: " + report.memberUri());
        }
        if (report.acceptedContentTypes() != null && !report.acceptedContentTypes().isEmpty()) {
            CliLog.stdout().println("accepts: " + String.join(", ", report.acceptedContentTypes()));
        }
    }

    private static String toJson(AdtDiscoveryReport report) {
        try {
            return toJsonThrows(report);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String toJsonThrows(AdtDiscoveryReport report)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ok", report.ok());
        map.put("statusMessage", report.statusMessage());
        map.put("destinationId", report.destinationId());
        map.put("fromEclipse", report.fromEclipse());
        if (report.collectionUri() != null) {
            map.put("collectionUri", report.collectionUri());
        }
        if (report.categoryTerm() != null) {
            map.put("categoryTerm", report.categoryTerm());
        }
        if (report.memberUri() != null) {
            map.put("memberUri", report.memberUri());
        }
        if (report.acceptedContentTypes() != null && !report.acceptedContentTypes().isEmpty()) {
            map.put("acceptedContentTypes", report.acceptedContentTypes());
        }
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
    }
}
