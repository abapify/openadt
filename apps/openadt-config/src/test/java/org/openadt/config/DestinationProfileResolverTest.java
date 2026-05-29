package org.openadt.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DestinationProfileResolverTest {
    @Test
    void explicitSsoProfileOverlaysHttpTransportAndBaseUrl() {
        OpenAdtConfig config = configWithDev(
            baseDestination(),
            profiles(Map.of(
                "sso",
                ssoProfile("https://dev-adt.example.com")
            ))
        );

        SystemProfile effective = DestinationProfileResolver.resolve(config, "DEV", "sso");

        assertEquals("http", effective.getAdt().getTransport());
        assertEquals("browser-sso", effective.getAdt().getAuthenticationKind());
        assertEquals("https://dev-adt.example.com/", effective.getAdt().getBaseUrl());
        assertEquals(
            "https://dev-adt.example.com/sap/bc/adt",
            AdtHttpFrontendUrls.resolveAdtApiBase(effective.getAdt())
        );
        assertEquals("dev-ms.example.com", effective.getJco().getMshost());
    }

    @Test
    void explicitSncProfileOverlaysJcoSncSettings() {
        SystemProfile.ProfileConfig snc = new SystemProfile.ProfileConfig();
        snc.setTransport("sdk");
        snc.setAuthenticationKind("snc");
        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        jco.setSncMode("1");
        jco.setSncQop("9");
        jco.setSncPartnername("p:CN=SAPServiceDEV");
        jco.setSncSso("1");
        snc.setJco(jco);

        OpenAdtConfig config = configWithDev(baseDestination(), profiles(Map.of("snc", snc)));

        SystemProfile effective = DestinationProfileResolver.resolve(config, "DEV", "snc");

        assertEquals("sdk", effective.getAdt().getTransport());
        assertEquals("9", effective.getJco().getSncQop());
        assertEquals("p:CN=SAPServiceDEV", effective.getJco().getSncPartnername());
    }

    @Test
    void missingProfileGivesClearError() {
        OpenAdtConfig config = configWithDev(baseDestination(), profiles(Map.of()));

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> DestinationProfileResolver.resolve(config, "DEV", "missing")
        );
        assertTrue(error.getMessage().contains("Profile not found"));
    }

    @Test
    void noProfileUsesDefaultProfile() {
        SystemProfile destination = baseDestination();
        destination.setDefaultProfile("sso");
        OpenAdtConfig config = configWithDev(
            destination,
            profiles(Map.of("sso", ssoProfile("https://dev-adt.example.com")))
        );

        SystemProfile effective = DestinationProfileResolver.resolve(config, "DEV", null);

        assertEquals("http", effective.getAdt().getTransport());
    }

    @Test
    void noProfileAndNoDefaultPreservesLegacyBehavior() {
        SystemProfile destination = baseDestination();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setTransport("sdk");
        destination.setAdt(adt);

        OpenAdtConfig config = configWithDev(destination, null);

        SystemProfile effective = DestinationProfileResolver.resolve(config, "DEV", null);

        assertEquals("sdk", effective.getAdt().getTransport());
        assertEquals("dev-ms.example.com", effective.getJco().getMshost());
    }

  @Test
  void resolveDoesNotMutateLoadedConfig() {
    SystemProfile destination = baseDestination();
    destination.setDefaultProfile("sso");
    Map<String, SystemProfile.ProfileConfig> profileMap = profiles(
        Map.of("sso", ssoProfile("https://dev-adt.example.com/sap/bc/adt"))
    );
    destination.setProfiles(profileMap);
    OpenAdtConfig config = configWithDev(destination, profileMap);

    DestinationProfileResolver.resolve(config, "DEV", "sso");

    assertNull(config.getSystems().get(0).getAdt());
  }

    private static OpenAdtConfig configWithDev(SystemProfile destination, Map<String, SystemProfile.ProfileConfig> profiles) {
        if (profiles != null) {
            destination.setProfiles(profiles);
        }
        OpenAdtConfig config = new OpenAdtConfig();
        config.setSystems(List.of(destination));
        return config;
    }

    private static SystemProfile baseDestination() {
        SystemProfile destination = new SystemProfile();
        destination.setAlias("DEV");
        destination.setClient("100");
        destination.setLanguage("EN");
        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        jco.setMshost("dev-ms.example.com");
        jco.setMsserv("3600");
        jco.setR3name("DEV");
        jco.setGroup("PUBLIC");
        destination.setJco(jco);
        return destination;
    }

    private static SystemProfile.ProfileConfig ssoProfile(String baseUrl) {
        SystemProfile.ProfileConfig profile = new SystemProfile.ProfileConfig();
        profile.setTransport("http");
        profile.setAuthenticationKind("browser-sso");
        profile.setBaseUrl(baseUrl);
        profile.setCallbackPort("0");
        return profile;
    }

    private static Map<String, SystemProfile.ProfileConfig> profiles(Map<String, SystemProfile.ProfileConfig> values) {
        return new LinkedHashMap<>(values);
    }
}
