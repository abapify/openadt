package org.openadt.sap.adt.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SdkServiceRegistryTest {
    @Test
    void registersCoreServices() {
        assertTrue(SdkServiceRegistry.serviceIds().contains("discovery.document"));
        assertTrue(SdkServiceRegistry.serviceIds().contains("transport.list"));
    }
}
