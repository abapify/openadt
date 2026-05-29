package org.openadt.cli;

import org.openadt.config.AdtHttpFrontendUrls;
import org.openadt.config.CliLog;
import org.openadt.config.ConfigLoader;
import org.openadt.config.SystemProfile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.Console;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
@Command(
    name = "create",
    mixinStandardHelpOptions = true,
    description = "Create or update a destination authentication profile in config"
)
public class ConfigDestinationsCreateCommand implements Callable<Integer> {
    @ParentCommand
    private ConfigDestinationsCommand parent;

    @Option(names = {"--alias"}, description = "Destination alias", required = false)
    private String alias;

    @Option(names = {"--profile"}, description = "Profile name (e.g. snc, sso)", required = false)
    private String profile;

    @Option(names = {"--transport"}, description = "ADT transport (sdk, http, rest-rfc)")
    private String transport;

    @Option(names = {"--auth"}, description = "Authentication kind (e.g. browser-sso, snc)")
    private String auth;

    @Option(names = {"--base-url"}, description = "SAP frontend origin for HTTP SSO and ADT (e.g. https://host)")
    private String baseUrl;

    @Option(
        names = {"--browser-entry-url"},
        description = "Optional browser entry URL for manual SSO/session setup before reentrance-ticket"
    )
    private String browserEntryUrl;

    @Option(names = {"--client"}, description = "SAP client")
    private String client;

    @Option(names = {"--language"}, description = "SAP language", defaultValue = "EN")
    private String language;

    @Option(names = {"--description"}, description = "Human-readable destination description")
    private String description;

    @Option(names = {"--system-id"}, description = "SAP system ID (defaults to alias)")
    private String systemId;

    @Option(names = {"--default-profile"}, description = "Set this profile as the destination default")
    private boolean defaultProfile;

    @Option(names = {"--callback-port"}, description = "Browser SSO callback port (0 = random)")
    private String callbackPort;

    @Option(names = {"--jco-mshost"}, description = "JCo message server host")
    private String jcoMshost;

    @Option(names = {"--jco-msserv"}, description = "JCo message server port/service")
    private String jcoMsserv;

    @Option(names = {"--jco-r3name"}, description = "JCo SAP system name")
    private String jcoR3name;

    @Option(names = {"--jco-group"}, description = "JCo logon group")
    private String jcoGroup;

    @Option(names = {"--snc-partnername"}, description = "SNC partner name")
    private String sncPartnername;

    @Option(names = {"--snc-qop"}, description = "SNC quality of protection")
    private String sncQop;

    @Override
    public Integer call() throws Exception {
        Console console = System.console();
        if (console != null) {
            promptMissing(console);
        }

        List<String> missing = missingRequiredFlags();
        if (!missing.isEmpty()) {
            CliLog.error("Missing required options: " + String.join(", ", missing));
            CliLog.error("Pass all required flags or run in an interactive terminal.");
            return 1;
        }

        SystemProfile destination = new SystemProfile();
        destination.setAlias(alias);
        destination.setSource("manual");
        destination.setDescription(description != null ? description : alias + " destination");
        destination.setSystemId(systemId != null ? systemId : alias);
        destination.setClient(client);
        destination.setLanguage(language);

        if (jcoMshost != null || jcoMsserv != null || jcoR3name != null || jcoGroup != null) {
            SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
            jco.setMshost(jcoMshost);
            jco.setMsserv(jcoMsserv);
            jco.setR3name(jcoR3name);
            jco.setGroup(jcoGroup);
            destination.setJco(jco);
        }

        SystemProfile.ProfileConfig profileConfig = new SystemProfile.ProfileConfig();
        profileConfig.setTransport(transport);
        profileConfig.setAuthenticationKind(auth);
        profileConfig.setBaseUrl(resolveConfiguredBaseUrl());
        profileConfig.setBrowserEntryUrl(resolveConfiguredBrowserEntryUrl());
        profileConfig.setCallbackPort(callbackPort);

        if (sncPartnername != null || sncQop != null) {
            SystemProfile.JcoConfig profileJco = new SystemProfile.JcoConfig();
            profileJco.setSncPartnername(sncPartnername);
            profileJco.setSncQop(sncQop);
            profileJco.setSncMode("1");
            profileJco.setSncSso("1");
            profileConfig.setJco(profileJco);
        }

        ConfigLoader loader = new ConfigLoader();
        Path configPath = parent.getConfigPath() != null
            ? parent.getConfigPath()
            : loader.getDefaultConfigPath();
        Path written = loader.saveManualDestinationProfile(
            configPath,
            destination,
            profile,
            profileConfig,
            defaultProfile
        );
        CliLog.info("Wrote destination profile to " + written);
        return 0;
    }

    private void promptMissing(Console console) {
        if (alias == null || alias.isBlank()) {
            alias = console.readLine("Destination alias: ");
        }
        if (profile == null || profile.isBlank()) {
            profile = console.readLine("Profile name: ");
        }
        if (client == null || client.isBlank()) {
            client = console.readLine("SAP client: ");
        }
        if (transport == null && auth == null) {
            transport = console.readLine("Transport (sdk/http): ");
        }
        if (isHttpProfile() && (baseUrl == null || baseUrl.isBlank())) {
            baseUrl = console.readLine("SAP frontend base URL (e.g. https://host): ");
        }
    }

    private String resolveConfiguredBaseUrl() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return AdtHttpFrontendUrls.normalizeToOrigin(baseUrl.trim());
    }

    private String resolveConfiguredBrowserEntryUrl() {
        if (browserEntryUrl == null || browserEntryUrl.isBlank()) {
            return null;
        }
        return browserEntryUrl.trim();
    }

    private List<String> missingRequiredFlags() {
        List<String> missing = new ArrayList<>();
        if (alias == null || alias.isBlank()) {
            missing.add("--alias");
        }
        if (profile == null || profile.isBlank()) {
            missing.add("--profile");
        }
        if (client == null || client.isBlank()) {
            missing.add("--client");
        }
        if ((transport == null || transport.isBlank()) && (auth == null || auth.isBlank())) {
            missing.add("--transport or --auth");
        }
        if (isHttpProfile() && resolveConfiguredBaseUrl() == null) {
            missing.add("--base-url");
        }
        return missing;
    }

    private boolean isHttpProfile() {
        if ("http".equalsIgnoreCase(transport)) {
            return true;
        }
        return "browser-sso".equalsIgnoreCase(auth);
    }
}
