package org.openadt.sap.adt.services;

import java.util.ArrayList;
import java.util.List;

import org.openadt.config.DestinationProfileResolver;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.fallback.http.HttpSsoTicketCache;

/**
 * Clears persisted HTTP SSO tickets for a destination (all named profiles when present).
 */
public final class AuthSessionSupport {
    private AuthSessionSupport() {
    }

    public static List<String> logout(OpenAdtConfig config, SystemProfile destination) {
        HttpSsoTicketCache cache = new HttpSsoTicketCache();
        List<String> cleared = new ArrayList<>();
        String alias = destination.getAlias();
        if (destination.getProfiles() != null && !destination.getProfiles().isEmpty()) {
            for (String profileName : destination.getProfiles().keySet()) {
                SystemProfile resolved = DestinationProfileResolver.resolve(config, alias, profileName);
                cache.invalidate(resolved);
                cleared.add(profileName);
            }
        } else {
            SystemProfile resolved = DestinationProfileResolver.resolve(config, alias, null);
            cache.invalidate(resolved);
            cleared.add("(legacy)");
        }
        return cleared;
    }
}
