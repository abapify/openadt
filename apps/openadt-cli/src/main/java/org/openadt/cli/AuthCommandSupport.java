package org.openadt.cli;

import java.io.IOException;
import java.nio.file.Path;

import org.openadt.config.ConfigLoader;
import org.openadt.config.DestinationProfileResolver;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;

abstract class AuthCommandSupport extends AdtCommandSupport {
    protected SystemProfile findDestination(OpenAdtConfig config, String alias) {
        if (config.getSystems() == null) {
            throw new IllegalArgumentException("System not found: " + alias);
        }
        return config.getSystems().stream()
            .filter(system -> alias.equals(system.getAlias()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("System not found: " + alias));
    }

    protected SystemProfile resolveWithChosenProfile(
        OpenAdtConfig config,
        String alias,
        String profileName
    ) {
        return DestinationProfileResolver.resolve(config, alias, profileName);
    }

    protected void persistDefaultProfile(
        Path configPath,
        SystemProfile destination,
        String profileName
    ) throws IOException {
        if (profileName == null || profileName.isBlank()) {
            return;
        }
        if (profileName.equals(destination.getDefaultProfile())) {
            return;
        }
        MapGuard.requireProfile(destination, profileName);
        ConfigLoader loader = new ConfigLoader();
        SystemProfile.ProfileConfig profile = destination.getProfiles().get(profileName);
        loader.saveManualDestinationProfile(configPath, destination, profileName, profile, true);
    }

    private static final class MapGuard {
        private MapGuard() {
        }

        static void requireProfile(SystemProfile destination, String profileName) {
            if (destination.getProfiles() == null || !destination.getProfiles().containsKey(profileName)) {
                throw new IllegalArgumentException(
                    "Profile not found: " + profileName + " for system " + destination.getAlias()
                );
            }
        }
    }
}
