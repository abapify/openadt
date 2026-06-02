package org.openadt.cli;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openadt.config.SystemProfile;

/**
 * Resolves which authentication profile to use for {@code openadt auth login}.
 */
final class AuthProfileChooser {
    private AuthProfileChooser() {
    }

    static String resolve(SystemProfile destination, String cliProfile, boolean interactive) {
        if (cliProfile != null && !cliProfile.isBlank()) {
            return cliProfile.trim();
        }
        Map<String, SystemProfile.ProfileConfig> profiles = destination.getProfiles();
        if (profiles == null || profiles.isEmpty()) {
            return null;
        }
        if (destination.getDefaultProfile() != null && !destination.getDefaultProfile().isBlank()) {
            return destination.getDefaultProfile();
        }
        if (profiles.size() == 1) {
            return profiles.keySet().iterator().next();
        }
        if (interactive) {
            String chosen = prompt(profiles);
            if (chosen != null) {
                return chosen;
            }
        }
        throw new IllegalArgumentException(profileListMessage(destination.getAlias(), profiles));
    }

    private static String prompt(Map<String, SystemProfile.ProfileConfig> profiles) {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        List<String> names = new ArrayList<>(new TreeMap<>(profiles).keySet());
        console.printf("%nAvailable authentication profiles:%n");
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            SystemProfile.ProfileConfig profile = profiles.get(name);
            String kind = profile != null && profile.getAuthenticationKind() != null
                ? profile.getAuthenticationKind()
                : "?";
            String transport = profile != null && profile.getTransport() != null
                ? profile.getTransport()
                : "?";
            console.printf("  %d) %s (%s, transport=%s)%n", i + 1, name, kind, transport);
        }
        String line = console.readLine("Choose profile [1-%d]: ", names.size());
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            int index = Integer.parseInt(line.trim()) - 1;
            if (index >= 0 && index < names.size()) {
                return names.get(index);
            }
        } catch (NumberFormatException ignored) {
            if (profiles.containsKey(line.trim())) {
                return line.trim();
            }
        }
        return null;
    }

    static String profileListMessage(String alias, Map<String, SystemProfile.ProfileConfig> profiles) {
        StringBuilder builder = new StringBuilder();
        builder.append("System ").append(alias).append(" has multiple auth profiles. Use --profile <name> or run with a TTY for interactive choice. Profiles:");
        for (String name : new TreeMap<>(profiles).keySet()) {
            builder.append(' ').append(name);
        }
        return builder.toString();
    }
}
