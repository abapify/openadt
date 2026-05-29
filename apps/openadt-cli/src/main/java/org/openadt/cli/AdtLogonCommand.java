package org.openadt.cli;

import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
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
        OpenAdtConfig config = null;
        SystemProfile system = null;
        try {
            config = loadConfig();
            system = resolveSystem(config, systemAlias);
            requireSdkTransport(system, "openadt adt logon");
            AdtLogonStatusReport report = AdtSdkServiceGateway.logon(config, system);
            if (json || "json".equalsIgnoreCase(format)) {
                CliLog.stdout().println(toJson(report));
            } else {
                printLogonStatus(report);
            }
            return report.loggedOn() ? 0 : 1;
        } catch (Exception error) {
            CliLog.error("openadt adt logon [" + systemAlias + "]: " + formatTransportError(system, error));
            return 1;
        }
    }
}
