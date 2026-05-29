package org.openadt.cli;

import java.io.IOException;
import java.nio.file.Path;

import org.openadt.config.ConfigLoader;
import org.openadt.config.DestinationProfileResolver;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.config.ProfileFetchHints;
import picocli.CommandLine.Option;

/**
 * Shared config resolution for {@code openadt adt …} commands.
 */
abstract class AdtCommandSupport {
    @Option(names = {"--config", "-c"}, description = "Config file path")
    Path configPath;

    @Option(names = {"--profile"}, description = "Authentication profile (e.g. snc, sso)")
    String profile;

    protected SystemProfile resolveSystem(String alias) throws IOException {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("System alias is required");
        }
        String effectiveProfile = ProfileFetchHints.resolveEffectiveProfile(profile);
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        OpenAdtConfig config = loader.load(effectivePath);
        return DestinationProfileResolver.resolve(config, alias, effectiveProfile);
    }

    protected OpenAdtConfig loadConfig() throws IOException {
        ConfigLoader loader = new ConfigLoader();
        Path effectivePath = configPath != null ? configPath : loader.getDefaultConfigPath();
        return loader.load(effectivePath);
    }
}
