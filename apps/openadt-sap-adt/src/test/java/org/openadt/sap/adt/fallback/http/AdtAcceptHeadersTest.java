package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdtAcceptHeadersTest {
    @Test
    void defaultAcceptForSystemInformation() {
        assertArrayEquals(
            new String[]{"application/vnd.sap.adt.core.http.systeminformation.v1+json"},
            AdtAcceptHeaders.defaultAccept("/sap/bc/adt/core/http/systeminformation")
        );
    }

    @Test
    void defaultAcceptForCoreDiscovery() {
        assertArrayEquals(
            new String[]{"application/atomsvc+xml"},
            AdtAcceptHeaders.defaultAccept("/sap/bc/adt/core/discovery")
        );
    }

    @Test
    void defaultAcceptForLegacyDiscoveryPath() {
        assertArrayEquals(
            new String[]{"application/atomsvc+xml"},
            AdtAcceptHeaders.defaultAccept("/sap/bc/adt/discovery")
        );
    }

    @Test
    void defaultAcceptForGenericAdtPath() {
        String[] accept = AdtAcceptHeaders.defaultAccept("/sap/bc/adt/programs/programs");
        assertEquals(3, accept.length);
        assertEquals("application/atom+xml;type=feed", accept[0]);
    }

    @Test
    void defaultAcceptHeaderValueJoinsTypes() {
        assertEquals(
            "application/atom+xml;type=feed, application/xml, application/vnd.sap.adt.core+xml",
            AdtAcceptHeaders.defaultAcceptHeaderValue("/sap/bc/adt/programs/programs")
        );
    }
}
