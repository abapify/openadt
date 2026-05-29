package org.openadt.cli;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.ProfileFetchHints;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtLogonStatusReport;
import org.openadt.sap.adt.sdk.AdtSdkServiceGateway;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "logon",
    mixinStandardHelpOptions = true,
    description = "Log on to SAP through the ADT SDK for a configured system"
)
public class AdtLogonCommand extends AdtCommandSupport implements Callable<Integer> {
    @Parameters(index = "0", description = "System alias (e.g. DEV)")
    String systemAlias;

    @Option(names = {"--format"}, description = "Output format: text or json", defaultValue = "text")
    String format;

    @Option(names = {"--json"}, description = "JSON output (same as --format json)")
    boolean json;

    @Override
    public Integer call() throws Exception {
        try {
            OpenAdtConfig config = loadConfig();
            SystemProfile system = resolveSystem(systemAlias);
            requireSdkTransport(system);
            AdtLogonStatusReport report = AdtSdkServiceGateway.logon(config, system);
            if (json || "json".equalsIgnoreCase(format)) {
                CliLog.stdout().println(toJson(report));
            } else {
                printText(report);
            }
            return report.loggedOn() ? 0 : 1;
        } catch (Exception error) {
            CliLog.error(ProfileFetchHints.formatTransportError(null, profile, error));
            return 1;
        }
    }

    private static void requireSdkTransport(SystemProfile system) {
        String transport = system.getAdt() != null ? system.getAdt().getTransport() : null;
        if (transport != null && !transport.isBlank() && !"sdk".equalsIgnoreCase(transport)) {
            throw new IllegalStateException(
                "openadt adt logon requires SDK transport (adt.transport = \"sdk\" or unset), got: " + transport
            );
        }
    }

    private static void printText(AdtLogonStatusReport report) {
        CliLog.stdout().println("destination: " + report.destinationId()
            + (report.fromEclipse() ? " (eclipse)" : " (config)"));
        CliLog.stdout().println("logged-on: " + report.loggedOn());
        CliLog.stdout().println("message: " + report.message());
    }

    private static String toJson(AdtLogonStatusReport report) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("loggedOn", report.loggedOn());
        map.put("destinationId", report.destinationId());
        map.put("fromEclipse", report.fromEclipse());
        map.put("message", report.message());
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
    }
}
