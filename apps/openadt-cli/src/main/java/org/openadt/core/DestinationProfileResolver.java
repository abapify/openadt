package org.openadt.core;

/**
 * Resolves a destination alias plus optional profile into an effective {@link SystemProfile}
 * without mutating the loaded config.
 */
public final class DestinationProfileResolver {
    private DestinationProfileResolver() {
    }

    public static SystemProfile resolve(OpenAdtConfig config, String alias, String profileName) {
        SystemProfile destination = findDestination(config, alias);
        if (destination == null) {
            throw new IllegalArgumentException("System not found: " + alias);
        }

        String effectiveProfile = profileName;
        if (effectiveProfile == null || effectiveProfile.isBlank()) {
            effectiveProfile = destination.getDefaultProfile();
        }

        SystemProfile effective = clone(destination);
        if (effectiveProfile == null || effectiveProfile.isBlank()) {
            return effective;
        }

        if (destination.getProfiles() == null || !destination.getProfiles().containsKey(effectiveProfile)) {
            throw new IllegalArgumentException(
                "Profile not found: " + effectiveProfile + " for system " + alias
            );
        }
        applyProfile(effective, destination.getProfiles().get(effectiveProfile));
        return effective;
    }

    public static String resolveProfileCallbackPort(OpenAdtConfig config, String alias, String profileName) {
        SystemProfile destination = findDestination(config, alias);
        if (destination == null || destination.getProfiles() == null) {
            return null;
        }
        String effectiveProfile = profileName;
        if (effectiveProfile == null || effectiveProfile.isBlank()) {
            effectiveProfile = destination.getDefaultProfile();
        }
        if (effectiveProfile == null || effectiveProfile.isBlank()) {
            return null;
        }
        SystemProfile.ProfileConfig profile = destination.getProfiles().get(effectiveProfile);
        return profile != null ? profile.getCallbackPort() : null;
    }

    private static SystemProfile findDestination(OpenAdtConfig config, String alias) {
        if (config.getSystems() == null || alias == null || alias.isBlank()) {
            return null;
        }
        return config.getSystems().stream()
            .filter(system -> alias.equals(system.getAlias()))
            .findFirst()
            .orElse(null);
    }

    private static SystemProfile clone(SystemProfile source) {
        SystemProfile copy = new SystemProfile();
        copy.setAlias(source.getAlias());
        copy.setSource(source.getSource());
        copy.setDescription(source.getDescription());
        copy.setSystemId(source.getSystemId());
        copy.setClient(source.getClient());
        copy.setLanguage(source.getLanguage());
        copy.setUser(source.getUser());
        copy.setDefaultProfile(source.getDefaultProfile());
        if (source.getJco() != null) {
            copy.setJco(cloneJco(source.getJco()));
        }
        if (source.getAdt() != null) {
            copy.setAdt(cloneAdt(source.getAdt()));
        }
        return copy;
    }

    private static void applyProfile(SystemProfile target, SystemProfile.ProfileConfig profile) {
        if (profile == null) {
            return;
        }
        SystemProfile.AdtConfig adt = target.getAdt();
        if (adt == null) {
            adt = new SystemProfile.AdtConfig();
            target.setAdt(adt);
        }
        if (profile.getTransport() != null) {
            adt.setTransport(profile.getTransport());
        }
        if (profile.getAuthenticationKind() != null) {
            adt.setAuthenticationKind(profile.getAuthenticationKind());
        }
        if (profile.getDiscoveryUrl() != null) {
            adt.setDiscoveryUrl(profile.getDiscoveryUrl());
        }
        if (profile.getSsoLandingUrl() != null) {
            adt.setSsoLandingUrl(profile.getSsoLandingUrl());
        }
        if (profile.getAdt() != null) {
            if (profile.getAdt().getTransport() != null) {
                adt.setTransport(profile.getAdt().getTransport());
            }
            if (profile.getAdt().getAshost() != null) {
                adt.setAshost(profile.getAdt().getAshost());
            }
            if (profile.getAdt().getDiscoveryUrl() != null) {
                adt.setDiscoveryUrl(profile.getAdt().getDiscoveryUrl());
            }
            if (profile.getAdt().getAuthenticationKind() != null) {
                adt.setAuthenticationKind(profile.getAdt().getAuthenticationKind());
            }
            if (profile.getAdt().getSsoLandingUrl() != null) {
                adt.setSsoLandingUrl(profile.getAdt().getSsoLandingUrl());
            }
        }
        if (profile.getJco() != null) {
            mergeJco(target, profile.getJco());
        }
    }

    private static void mergeJco(SystemProfile target, SystemProfile.JcoConfig source) {
        SystemProfile.JcoConfig jco = target.getJco();
        if (jco == null) {
            jco = new SystemProfile.JcoConfig();
            target.setJco(jco);
        }
        if (source.getMshost() != null) {
            jco.setMshost(source.getMshost());
        }
        if (source.getMsserv() != null) {
            jco.setMsserv(source.getMsserv());
        }
        if (source.getR3name() != null) {
            jco.setR3name(source.getR3name());
        }
        if (source.getGroup() != null) {
            jco.setGroup(source.getGroup());
        }
        if (source.getAshost() != null) {
            jco.setAshost(source.getAshost());
        }
        if (source.getSysnr() != null) {
            jco.setSysnr(source.getSysnr());
        }
        if (source.getSncMode() != null) {
            jco.setSncMode(source.getSncMode());
        }
        if (source.getSncQop() != null) {
            jco.setSncQop(source.getSncQop());
        }
        if (source.getSncPartnername() != null) {
            jco.setSncPartnername(source.getSncPartnername());
        }
        if (source.getSncSso() != null) {
            jco.setSncSso(source.getSncSso());
        }
        if (source.getSticky() != null) {
            jco.setSticky(source.getSticky());
        }
        if (source.getDenyInitialPassword() != null) {
            jco.setDenyInitialPassword(source.getDenyInitialPassword());
        }
    }

    private static SystemProfile.JcoConfig cloneJco(SystemProfile.JcoConfig source) {
        SystemProfile.JcoConfig copy = new SystemProfile.JcoConfig();
        copy.setMshost(source.getMshost());
        copy.setMsserv(source.getMsserv());
        copy.setR3name(source.getR3name());
        copy.setGroup(source.getGroup());
        copy.setAshost(source.getAshost());
        copy.setSysnr(source.getSysnr());
        copy.setSncMode(source.getSncMode());
        copy.setSncQop(source.getSncQop());
        copy.setSncPartnername(source.getSncPartnername());
        copy.setSncSso(source.getSncSso());
        copy.setSticky(source.getSticky());
        copy.setDenyInitialPassword(source.getDenyInitialPassword());
        return copy;
    }

    private static SystemProfile.AdtConfig cloneAdt(SystemProfile.AdtConfig source) {
        SystemProfile.AdtConfig copy = new SystemProfile.AdtConfig();
        copy.setTransport(source.getTransport());
        copy.setAshost(source.getAshost());
        copy.setDiscoveryUrl(source.getDiscoveryUrl());
        copy.setAuthenticationKind(source.getAuthenticationKind());
        copy.setSsoLandingUrl(source.getSsoLandingUrl());
        return copy;
    }
}
