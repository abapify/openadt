package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtHttpCookieProviderTest {
    @Test
    void prefersEnvironmentVariable() {
        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> {
            if ("OPENADT_MYSAPSSO2".equals(key)) {
                return "from-env";
            }
            return null;
        });

        assertEquals("from-env", provider.resolveMysapsso2(new OpenAdtConfig(), null).ticket());
    }

    @Test
    void readsCookieFile(@TempDir Path tempDir) throws Exception {
        Path cookieFile = tempDir.resolve("ticket.txt");
        Files.writeString(cookieFile, "MYSAPSSO2=file-ticket");

        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> {
            if ("OPENADT_COOKIE_FILE".equals(key)) {
                return cookieFile.toString();
            }
            return null;
        });

        assertEquals("file-ticket", provider.resolveMysapsso2(new OpenAdtConfig(), null).ticket());
    }

    @Test
    void usesConfigValueWhenEnvMissing() {
        OpenAdtConfig config = new OpenAdtConfig();
        OpenAdtConfig.SecureLoginConfig secureLogin = new OpenAdtConfig.SecureLoginConfig();
        secureLogin.setMysapsso2("from-config");
        config.setSecureLogin(secureLogin);

        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> null);

        assertEquals("from-config", provider.resolveMysapsso2(config, null).ticket());
    }

    @Test
    void failsWithHelpfulMessageWhenTicketMissing() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://sap.example.com:8001/sap/bc/adt");
        system.setAdt(adt);

        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(
            key -> null,
            (config, profile) -> null
        );

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> provider.resolveMysapsso2(new OpenAdtConfig(), system)
        );

        assertEquals(true, error.getMessage().contains("MYSAPSSO2"));
        assertEquals(true, error.getMessage().contains("https://sap.example.com:8001/sap/bc/adt"));
    }

    @Test
    void prefersDiskCacheBeforeBrowserFlow(@TempDir Path openadtHome) throws Exception {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setClient("100");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");
        system.setAdt(adt);

        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null);
        cache.write(system, "cached-ticket");

        AdtHttpTicketProvider neverCalled = (config, profile) -> {
            throw new AssertionError("browser SSO should not run when cache is valid");
        };
        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> null, neverCalled, cache);

        AdtHttpCookieProvider.Mysapsso2Resolution resolution =
            provider.resolveMysapsso2(new OpenAdtConfig(), system);
        assertEquals("cached-ticket", resolution.ticket());
        assertEquals(true, resolution.usedDiskCache());
    }

    @Test
    void skipsDiskCacheWhenRequestNoCache(@TempDir Path openadtHome) throws Exception {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setClient("100");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://dev-adt.example.com/sap/bc/adt");
        system.setAdt(adt);

        HttpSsoTicketCache cache = new HttpSsoTicketCache(openadtHome, key -> null, true);
        cache.write(system, "cached-ticket");

        AdtHttpTicketProvider browser = (config, profile) -> "fresh-ticket";
        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> null, browser, cache);

        AdtHttpCookieProvider.Mysapsso2Resolution resolution =
            provider.resolveMysapsso2(new OpenAdtConfig(), system);
        assertEquals("fresh-ticket", resolution.ticket());
        assertFalse(resolution.usedDiskCache());
        assertTrue(cache.read(system).isEmpty());
    }

    @Test
    void fallsBackToReentranceTicketFlowWhenNoCookieInputsExist() {
        SystemProfile system = new SystemProfile();
        system.setClient("100");
        system.setLanguage("EN");
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://sap.example.com");
        system.setAdt(adt);

        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(
            key -> null,
            (config, profile) -> "ticket-from-callback"
        );

        assertEquals("ticket-from-callback", provider.resolveMysapsso2(
            new OpenAdtConfig(),
            system
        ).ticket());
    }
}
