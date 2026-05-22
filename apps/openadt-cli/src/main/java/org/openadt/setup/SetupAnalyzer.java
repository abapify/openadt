package org.openadt.setup;

import org.openadt.core.OpenAdtConfig;
import org.openadt.core.SystemProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SetupAnalyzer {
    public record SetupResult(
        List<SystemProfile> systems,
        List<String> warnings,
        OpenAdtConfig.RuntimeConfig runtime,
        OpenAdtConfig.SecureLoginConfig secureLogin
    ) {}

    private final List<SystemDetector> systemDetectors;
    private final SecureLoginDetector secureLoginDetector;
    private final RuntimeDetector runtimeDetector;

    public SetupAnalyzer() {
        this(
            List.of(
                new SapGuiLandscapeDetector(),
                new NwbcSystemDetector(),
                new SapBusinessClientDetector(),
                new EclipseAdtDetector(),
                new SapRulesDetector()
            ),
            new SecureLoginDetector(),
            new RuntimeDetector()
        );
    }

    SetupAnalyzer(List<SystemDetector> systemDetectors, SecureLoginDetector secureLoginDetector, RuntimeDetector runtimeDetector) {
        this.systemDetectors = List.copyOf(systemDetectors);
        this.secureLoginDetector = secureLoginDetector;
        this.runtimeDetector = runtimeDetector;
    }

    public SetupResult analyze() {
        Map<String, SystemProfile> systemsByKey = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (SystemDetector detector : systemDetectors) {
            detector.detect().forEach(system -> mergeSystem(systemsByKey, system));
        }

        SecureLoginDetector.DetectionResult slcResult = secureLoginDetector.detectSecureLogin();
        systemsByKey.values().forEach(system -> applyDefaults(system, slcResult.available()));

        OpenAdtConfig.RuntimeConfig runtime = runtimeDetector.detect();
        OpenAdtConfig.SecureLoginConfig secureLogin = null;
        if (slcResult.available()) {
            OpenAdtConfig.SecureLoginConfig detected = new OpenAdtConfig.SecureLoginConfig();
            detected.setLocalSecurityHub(slcResult.url());
            OpenAdtConfig wrapper = new OpenAdtConfig();
            wrapper.setSecureLogin(detected);
            secureLogin = org.openadt.core.SecureLoginBootstrap.resolveSecureLogin(wrapper);
            if (secureLogin == null) {
                secureLogin = detected;
            }
        }

        return new SetupResult(new ArrayList<>(systemsByKey.values()), warnings, runtime, secureLogin);
    }

    private void mergeSystem(Map<String, SystemProfile> systemsByKey, SystemProfile incoming) {
        if (incoming == null) {
            return;
        }
        normalizeAlias(incoming);
        String key = systemKey(incoming);
        if (key == null) {
            return;
        }
        SystemProfile existing = systemsByKey.get(key);
        if (existing == null) {
            systemsByKey.put(key, incoming);
            return;
        }
        mergeInto(existing, incoming);
    }

    private void normalizeAlias(SystemProfile system) {
        if (blank(system.getAlias()) && !blank(system.getSystemId())) {
            system.setAlias(system.getSystemId());
        }
    }

    private String systemKey(SystemProfile system) {
        if (!blank(system.getSystemId())) {
            return "sid:" + system.getSystemId();
        }
        if (!blank(system.getAlias())) {
            return "alias:" + system.getAlias();
        }
        return null;
    }

    private void mergeInto(SystemProfile target, SystemProfile source) {
        if (blank(target.getAlias())) target.setAlias(source.getAlias());
        if (blank(target.getSource())) target.setSource(source.getSource());
        if (blank(target.getDescription())) target.setDescription(source.getDescription());
        if (blank(target.getSystemId())) target.setSystemId(source.getSystemId());
        if (blank(target.getClient())) target.setClient(source.getClient());
        if (blank(target.getLanguage())) target.setLanguage(source.getLanguage());
        if (blank(target.getUser())) target.setUser(source.getUser());

        if (source.getJco() != null) {
            if (target.getJco() == null) {
                target.setJco(source.getJco());
            } else {
                mergeJco(target.getJco(), source.getJco());
            }
        }
        if (source.getAdt() != null) {
            if (target.getAdt() == null) {
                target.setAdt(source.getAdt());
            } else {
                mergeAdt(target.getAdt(), source.getAdt());
            }
        }
    }

    private void applyDefaults(SystemProfile system, boolean secureLoginHubAvailable) {
        if (blank(system.getAlias()) && !blank(system.getSystemId())) {
            system.setAlias(system.getSystemId());
        }
        if (blank(system.getUser())) {
            String userName = blankToNull(System.getProperty("user.name"));
            if (userName != null) {
                system.setUser(userName.toUpperCase(Locale.ROOT));
            }
        }
        if (blank(system.getLanguage())) {
            system.setLanguage("EN");
        }
        if (system.getJco() != null) {
            if ("1".equals(system.getJco().getSncSso())) {
                if (blank(system.getJco().getSticky())) {
                    system.getJco().setSticky("1");
                }
                if (blank(system.getJco().getDenyInitialPassword())) {
                    system.getJco().setDenyInitialPassword("1");
                }
            }
        }
        if (system.getAdt() == null) {
            system.setAdt(new SystemProfile.AdtConfig());
        }
        if (blank(system.getAdt().getTransport())) {
            if (secureLoginHubAvailable && !blank(system.getAdt().getDiscoveryUrl())) {
                system.getAdt().setTransport("http");
            } else {
                system.getAdt().setTransport("sdk");
            }
        }
        if (blank(system.getAdt().getAuthenticationKind()) && system.getJco() != null
            && "1".equals(system.getJco().getSncSso())) {
            system.getAdt().setAuthenticationKind("sso");
        }
    }

    private void mergeJco(SystemProfile.JcoConfig target, SystemProfile.JcoConfig source) {
        if (blank(target.getMshost())) target.setMshost(source.getMshost());
        if (blank(target.getMsserv())) target.setMsserv(source.getMsserv());
        if (blank(target.getR3name())) target.setR3name(source.getR3name());
        if (blank(target.getGroup())) target.setGroup(source.getGroup());
        if (blank(target.getAshost())) target.setAshost(source.getAshost());
        if (blank(target.getSysnr())) target.setSysnr(source.getSysnr());
        if (blank(target.getSncMode())) target.setSncMode(source.getSncMode());
        if (blank(target.getSncQop())) target.setSncQop(source.getSncQop());
        if (blank(target.getSncPartnername())) target.setSncPartnername(source.getSncPartnername());
        if (blank(target.getSncSso())) target.setSncSso(source.getSncSso());
        if (blank(target.getSticky())) target.setSticky(source.getSticky());
        if (blank(target.getDenyInitialPassword())) target.setDenyInitialPassword(source.getDenyInitialPassword());
    }

    private void mergeAdt(SystemProfile.AdtConfig target, SystemProfile.AdtConfig source) {
        if (blank(target.getTransport())) target.setTransport(source.getTransport());
        if (blank(target.getAshost())) target.setAshost(source.getAshost());
        if (blank(target.getDiscoveryUrl())) target.setDiscoveryUrl(source.getDiscoveryUrl());
        if (blank(target.getAuthenticationKind())) target.setAuthenticationKind(source.getAuthenticationKind());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return blank(value) ? null : value;
    }
}
