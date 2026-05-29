package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openadt.config.OpenAdtConfig;
class MfaUrlResolverTest {
    private static final String HUB_PROFILE = "11111111-1111-1111-1111-111111111111";
    private static final String PORTAL_PROFILE = "22222222-2222-2222-2222-222222222222";

    @Test
    void portalUrlUsesSsoUrlNotHubProfile() {
        OpenAdtConfig.SecureLoginConfig secureLogin = new OpenAdtConfig.SecureLoginConfig();
        secureLogin.setOrigin("https://sls.example.com:50001");
        secureLogin.setWebAdapterProfileId(HUB_PROFILE);
        secureLogin.setSsoUrl(
            "https://sls.example.com:50001/SecureLoginServer/portal/webclient?profile=" + PORTAL_PROFILE
        );

        String url = MfaUrlResolver.resolveSecureLoginPortalUrl(secureLogin);

        assertTrue(url.contains(PORTAL_PROFILE));
        assertTrue(!url.contains(HUB_PROFILE));
    }

    @Test
    void profileIdFromUrlExtractsUuid() {
        assertEquals(
            PORTAL_PROFILE,
            MfaUrlResolver.profileIdFromUrl(
                "https://sls.example.com/portal/webclient?profile=" + PORTAL_PROFILE
            )
        );
    }
}
