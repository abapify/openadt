package org.openadt.sap.adt.services.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openadt.sap.adt.sdk.SdkServiceArgs;

class TransportListHandlerTest {
    @Test
    void defaultsTrfunctionToWorkbench() {
        assertEquals("K", SdkServiceArgs.empty().getOrDefault("trfunction", "K"));
    }
}
