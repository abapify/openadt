package org.openadt.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openadt.config.ConfigLoader;
import org.openadt.config.DestinationProfileResolver;
import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.ProfileFetchHints;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtLogonStatusReport;
import picocli.CommandLine.Option;

/**
 * Shared config resolution for {@code openadt adt …} commands.
 */
abstract class AdtCommandSupport {
    @Option(names = {"--config", "-c"}, description = "Config file path")
    Path configPath;

    @Option(names = {"--profile"}, description = "Authentication profile (e.g. snc, sso)")
    String profile;

    protected SystemProfile resolveSystem(OpenAdtConfig config, String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("System alias is required");
        }
        String effectiveProfile = ProfileFetchHints.resolveEffectiveProfile(profile);
        return DestinationProfileResolver.resolve(config, alias, effectiveProfile);
    }

    protected OpenAdtConfig loadConfig() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        return loader.load(effectivePath);
    }

    protected static void requireSdkTransport(SystemProfile system, String commandName) {
        String transport = system.getAdt() != null ? system.getAdt().getTransport() : null;
        if (transport != null && !transport.isBlank() && !"sdk".equalsIgnoreCase(transport)) {
            throw new IllegalStateException(
                commandName + " requires SDK transport (adt.transport = \"sdk\" or unset), got: " + transport
            );
        }
    }

    protected static void printLogonStatus(AdtLogonStatusReport report) {
        CliLog.stdout().println("destination: " + report.destinationId()
            + (report.fromEclipse() ? " (eclipse)" : " (config)"));
        CliLog.stdout().println("logged-on: " + report.loggedOn());
        CliLog.stdout().println("message: " + report.message());
    }

    protected static String toJson(AdtLogonStatusReport report) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("loggedOn", report.loggedOn());
        map.put("destinationId", report.destinationId());
        map.put("fromEclipse", report.fromEclipse());
        map.put("message", report.message());
        return new ObjectMapper().writeValueAsString(map);
    }

    protected String formatTransportError(SystemProfile system, Throwable error) {
        return ProfileFetchHints.formatTransportError(system, profile, error);
    }
}
