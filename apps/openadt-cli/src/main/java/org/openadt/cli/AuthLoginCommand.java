package org.openadt.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.ConfigLoader;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.ProfileFetchHints;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtLogonStatusReport;
import org.openadt.sap.adt.sdk.AdtSdkServiceGateway;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "login",
    mixinStandardHelpOptions = true,
    description = "Choose auth profile, save as default_profile, and verify SDK logon"
)
public class AuthLoginCommand extends AuthCommandSupport implements Callable<Integer> {
    @Parameters(index = "0", description = "System alias (e.g. DEV)")
    String systemAlias;

    @Option(names = {"--no-save"}, description = "Verify logon only; do not update default_profile in config")
    boolean noSave;

    @Option(names = {"--format"}, description = "Output format: text or json", defaultValue = "text")
    String format;

    @Option(names = {"--json"}, description = "JSON output (same as --format json)")
    boolean json;

    @Override
    public Integer call() throws Exception {
        OpenAdtConfig config = null;
        SystemProfile destination = null;
        String profileName = null;
        try {
            ConfigLoader loader = new ConfigLoader();
            Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
            config = loadConfig();
            destination = findDestination(config, systemAlias);
            profileName = AuthProfileChooser.resolve(
                destination,
                profile,
                profile == null || profile.isBlank()
            );
            SystemProfile system = resolveWithChosenProfile(config, systemAlias, profileName);
            requireSdkTransport(system, "openadt auth login");
            AdtLogonStatusReport report = AdtSdkServiceGateway.logon(config, system);
            if (report.loggedOn()) {
                loader.saveSessionContext(effectivePath, systemAlias);
                if (!noSave) {
                    persistDefaultProfile(effectivePath, destination, profileName);
                    config = loader.load(effectivePath);
                    destination = findDestination(config, systemAlias);
                } else {
                    config = loader.load(effectivePath);
                }
            }
            if (json || "json".equalsIgnoreCase(format)) {
                CliLog.stdout().println(toJson(report));
            } else {
                printLoginResult(report, profileName, noSave);
            }
            return report.loggedOn() ? 0 : 1;
        } catch (Exception error) {
            String message = destination != null
                ? ProfileFetchHints.formatTransportError(destination, null, error)
                : error.getMessage();
            CliLog.error("openadt auth login [" + systemAlias + "]: " + message);
            return 1;
        }
    }

    private void printLoginResult(AdtLogonStatusReport report, String chosenProfile, boolean skippedSave) {
        printLogonStatus(report);
        if (chosenProfile != null && !chosenProfile.isBlank()) {
            CliLog.stdout().println(
                "auth-profile: " + chosenProfile + (skippedSave ? " (not saved)" : " (saved as default_profile)")
            );
        }
        CliLog.stdout().println(
            "session-context: " + systemAlias + " (fetch, proxy, discovery omit <SYSTEM>)"
        );
        CliLog.stdout().println("hint: keep openadt proxy running for a warm SDK session across commands");
    }
}
