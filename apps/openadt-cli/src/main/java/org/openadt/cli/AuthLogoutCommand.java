package org.openadt.cli;

import java.util.List;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.ProfileFetchHints;
import org.openadt.config.ConfigLoader;
import org.openadt.config.OpenAdtConfig;

import java.nio.file.Path;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtSdkServiceGateway;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "logout",
    mixinStandardHelpOptions = true,
    description = "Clear persisted HTTP SSO cache for a system (SDK/JCo sessions are per-process)"
)
public class AuthLogoutCommand extends AuthCommandSupport implements Callable<Integer> {
    @Parameters(index = "0", arity = "0..1", description = "System alias (default: active session from auth login)")
    String systemAlias;

    @Override
    public Integer call() throws Exception {
        SystemProfile destination = null;
        try {
            ConfigLoader loader = new ConfigLoader();
            Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
            OpenAdtConfig config = loadConfig();
            String alias = resolveSystemAlias(config, systemAlias);
            destination = findDestination(config, alias);
            List<String> cleared = AdtSdkServiceGateway.logout(config, destination);
            String activeSession = config.getSession() != null ? config.getSession().getSystem() : null;
            if (activeSession != null && activeSession.equalsIgnoreCase(alias)) {
                loader.clearSessionContext(effectivePath);
            }
            CliLog.stdout().println("system: " + alias);
            boolean sessionCleared = activeSession != null && activeSession.equalsIgnoreCase(alias);
            loader.clearSessionContext(effectivePath);
            CliLog.stdout().println("cleared-session-context: " + (sessionCleared ? "yes" : "no"));
            CliLog.stdout().println("cleared-http-sso-cache: " + String.join(", ", cleared));
            CliLog.stdout().println(
                "note: SDK logon state is not shared between CLI processes; stop openadt proxy to end a warm session."
            );
            return 0;
        } catch (Exception error) {
            String message = destination != null
                ? ProfileFetchHints.formatTransportError(destination, null, error)
                : error.getMessage();
            CliLog.error("openadt auth logout [" + systemAlias + "]: " + message);
            return 1;
        }
    }
}
