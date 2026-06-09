package org.openadt.cli.adt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.config.ConfigLoader;
import org.openadt.config.DestinationProfileResolver;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.ProfileFetchHints;
import org.openadt.config.SessionContext;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.services.agent.AgentError;
import org.openadt.sap.adt.services.agent.AgentResult;
import org.openadt.sap.adt.services.agent.AgentService;
import org.openadt.sap.adt.services.agent.AgentServiceRegistry;
import org.openadt.sap.adt.services.agent.AgentThrottle;

import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine.Option;

/**
 * Shared base class for {@code openadt adt <verb>} subcommands.
 *
 * <p>This is the agent-foundation analogue of
 * {@code org.openadt.cli.SdkServiceCommandSupport}. It is in a new package
 * so it does not need to widen the visibility of the existing
 * package-private {@code org.openadt.cli.AdtCommandSupport}; the small set
 * of methods it needs (config load, system resolve, SDK-transport
 * enforcement, transport-error formatting) is re-implemented locally.</p>
 */
abstract class AdtAgentCommandSupport implements Callable<Integer> {

    @Option(names = {"--config", "-c"}, description = "Config file path")
    Path configPath;

    @Option(names = {"--profile"}, description = "Authentication profile (e.g. snc, sso)")
    String profile;

    @Option(names = {"--json"}, description = "Emit the AgentResult JSON envelope to stdout")
    boolean jsonOutput;

    @Option(names = {"--param"}, description = "Verb parameter key=value (repeatable)", paramLabel = "KEY=VALUE")
    List<String> serviceParams;

    /** Verb id, e.g. {@code "adt_atc_run_check"}. Must match the registry. */
    protected abstract String serviceId();

    /** System alias, or null/blank for the active session. */
    protected abstract String systemAliasParam();

    protected Map<String, String> buildServiceArgs() {
        return parseServiceParams(serviceParams);
    }

    @Override
    public Integer call() {
        String systemAlias = null;
        SystemProfile system = null;
        try {
            OpenAdtConfig config = loadConfig();
            system = resolveSystem(config, systemAliasParam());
            systemAlias = system.getAlias();
            requireSdkTransport(system, "openadt adt " + serviceId());
            if (!AgentThrottle.acquire(systemAlias)) {
                emit(AgentResult.fail(new AgentError(
                    org.openadt.sap.adt.services.agent.AgentErrorCode.THROTTLED,
                    "Throttled for destination " + systemAlias
                )));
                return 1;
            }
            AgentService service = AgentServiceRegistry.lookup(serviceId());
            if (service == null) {
                AgentError error = new AgentError(
                    org.openadt.sap.adt.services.agent.AgentErrorCode.INTERNAL,
                    "Verb '" + serviceId() + "' is not registered. Known: "
                        + String.join(", ", AgentServiceRegistry.serviceIds())
                );
                emit(AgentResult.fail(error));
                return 1;
            }
            AgentResult result = service.run(systemAlias, buildServiceArgs());
            emit(result);
            return result.success() ? 0 : 1;
        } catch (Exception error) {
            AgentResult failure = AgentResult.fail(
                AgentError.internal(formatTransportError(system, profile, error))
            );
            try {
                emit(failure);
            } catch (Exception inner) {
                CliLog.error("openadt adt " + serviceId() + " [" + systemAlias + "]: "
                    + formatTransportError(system, profile, error));
            }
            return 1;
        }
    }

    // ---- Local copies of AdtCommandSupport helpers (see class Javadoc). ----

    private OpenAdtConfig loadConfig() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        return loader.load(effectivePath);
    }

    private String resolveSystemAlias(OpenAdtConfig config, String cliAlias) {
        return SessionContext.requireAlias(config, cliAlias);
    }

    private SystemProfile resolveSystem(OpenAdtConfig config, String cliAlias) {
        String alias = resolveSystemAlias(config, cliAlias);
        String effectiveProfile = ProfileFetchHints.resolveEffectiveProfile(profile);
        return DestinationProfileResolver.resolve(config, alias, effectiveProfile);
    }

    private void requireSdkTransport(SystemProfile system, String commandName) {
        String transport = system.getAdt() != null ? system.getAdt().getTransport() : null;
        if (transport != null && !transport.isBlank() && !"sdk".equalsIgnoreCase(transport)) {
            throw new IllegalStateException(
                commandName + " requires SDK transport (adt.transport = \"sdk\" or unset), got: " + transport
            );
        }
    }

    private String formatTransportError(SystemProfile system, String profileName, Throwable error) {
        return ProfileFetchHints.formatTransportError(system, profileName, error);
    }

    // ---- Output formatting ----

    private void emit(AgentResult result) throws Exception {
        if (jsonOutput) {
            CliLog.stdout().println(new ObjectMapper().writeValueAsString(result.toJsonMap()));
        } else {
            Map<String, Object> data = result.data();
            if (result.success()) {
                if (data.isEmpty()) {
                    CliLog.stdout().println("ok");
                } else {
                    for (Map.Entry<String, Object> entry : data.entrySet()) {
                        CliLog.stdout().println(entry.getKey() + ": " + entry.getValue());
                    }
                }
            } else {
                CliLog.error("error.code: " + result.error().code().name());
                CliLog.error("error.message: " + result.error().message());
            }
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
