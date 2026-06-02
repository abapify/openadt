package org.openadt.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openadt.config.SystemProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthProfileChooserTest {
    @Test
    void usesCliProfileWhenProvided() {
        SystemProfile destination = destinationWithProfiles("snc", "sso");
        assertEquals("sso", AuthProfileChooser.resolve(destination, "sso", false));
    }

    @Test
    void usesDefaultProfileWhenSet() {
        SystemProfile destination = destinationWithProfiles("snc", "sso");
        destination.setDefaultProfile("sso");
        assertEquals("sso", AuthProfileChooser.resolve(destination, null, false));
    }

    @Test
    void singleProfileChosenAutomatically() {
        SystemProfile destination = destinationWithProfiles("snc");
        assertEquals("snc", AuthProfileChooser.resolve(destination, null, false));
    }

    @Test
    void legacyDestinationReturnsNull() {
        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        assertNull(AuthProfileChooser.resolve(destination, null, false));
    }

    @Test
    void multipleProfilesRequireExplicitChoiceWithoutInteractive() {
        SystemProfile destination = destinationWithProfiles("snc", "sso");
        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> AuthProfileChooser.resolve(destination, null, false)
        );
        assertTrue(error.getMessage().contains("snc"));
        assertTrue(error.getMessage().contains("sso"));
    }

    private static SystemProfile destinationWithProfiles(String... names) {
        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        LinkedHashMap<String, SystemProfile.ProfileConfig> profiles = new LinkedHashMap<>();
        for (String name : names) {
            SystemProfile.ProfileConfig profile = new SystemProfile.ProfileConfig();
            profile.setTransport("sdk");
            profile.setAuthenticationKind(name.equals("sso") ? "browser-sso" : "snc");
            profiles.put(name, profile);
        }
        destination.setProfiles(profiles);
        return destination;
    }
}
