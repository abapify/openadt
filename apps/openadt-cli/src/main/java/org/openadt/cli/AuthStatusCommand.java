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
    name = "status",
    mixinStandardHelpOptions = true,
    description = "Show configured default_profile and SDK logon state in this JVM"
)
public class AuthStatusCommand extends AuthCommandSupport implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "System alias (default: active session from auth login)")
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
            String alias = resolveSystemAlias(config, systemAlias);
            SystemProfile destination = findDestination(config, alias);
            system = resolveWithChosenProfile(config, alias, profile);
            requireSdkTransport(system, "openadt auth status");
            AdtLogonStatusReport report = AdtSdkServiceGateway.logonStatus(config, system);
            if (json || "json".equalsIgnoreCase(format)) {
                CliLog.stdout().println(toJson(report));
            } else {
                printStatus(config, destination, system, report);
            }
            return report.loggedOn() ? 0 : 1;
        } catch (Exception error) {
            CliLog.error("openadt auth status [" + systemAlias + "]: " + formatTransportError(system, error));
            return 1;
        }
    }

    private void printStatus(
        OpenAdtConfig config,
        SystemProfile destination,
        SystemProfile effective,
        AdtLogonStatusReport report
    ) {
        CliLog.stdout().println("system: " + destination.getAlias());
        if (config.getSession() != null && config.getSession().getSystem() != null) {
            CliLog.stdout().println("session-context: " + config.getSession().getSystem());
        }
        CliLog.stdout().println("default_profile: " + nullToDash(destination.getDefaultProfile()));
        CliLog.stdout().println(
            "effective-profile: " + nullToDash(effective.getActiveProfile() != null ? effective.getActiveProfile() : destination.getDefaultProfile())
        );
        printLogonStatus(report);
        if (!report.loggedOn()) {
            CliLog.stdout().println(
                "note: each openadt command starts a new JVM — run openadt auth login or keep openadt proxy running."
            );
        }
    }

    private static String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
}
