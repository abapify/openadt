package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdtHttpCookieProviderTest {
    @Test
    void prefersEnvironmentVariable() {
        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> {
            if ("OPENADT_MYSAPSSO2".equals(key)) {
                return "from-env";
            }
            return null;
        });

        assertEquals("from-env", provider.resolveMysapsso2(new OpenAdtConfig(), null));
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

        assertEquals("file-ticket", provider.resolveMysapsso2(new OpenAdtConfig(), null));
    }

    @Test
    void usesConfigValueWhenEnvMissing() {
        OpenAdtConfig config = new OpenAdtConfig();
        OpenAdtConfig.SecureLoginConfig secureLogin = new OpenAdtConfig.SecureLoginConfig();
        secureLogin.setMysapsso2("from-config");
        config.setSecureLogin(secureLogin);

        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> null);

        assertEquals("from-config", provider.resolveMysapsso2(config, null));
    }

    @Test
    void failsWithHelpfulMessageWhenTicketMissing() {
        SystemProfile system = new SystemProfile();
        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setDiscoveryUrl("https://sap.example.com:8001/sap/bc/adt");
        system.setAdt(adt);

        AdtHttpCookieProvider provider = new AdtHttpCookieProvider(key -> null);

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> provider.resolveMysapsso2(new OpenAdtConfig(), system)
        );

        assertEquals(true, error.getMessage().contains("MYSAPSSO2"));
        assertEquals(true, error.getMessage().contains("https://sap.example.com:8001/sap/bc/adt"));
    }
}
