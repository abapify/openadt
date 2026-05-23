package org.openadt.core;

import com.sap.adt.destinations.model.IDestinationData;
import com.sap.adt.destinations.model.ISystemConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdtSdkTransportClientTest {
    @Test
    void mapsSncQopNineToHighestAvailable() {
        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setSystemId("DEV");
        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        jco.setMshost("dev-ms.example.com");
        jco.setGroup("PUBLIC");
        jco.setSncPartnername("p:CN=SAPServiceDEV");
        jco.setSncSso("1");
        jco.setSncQop("9");
        system.setJco(jco);

        IDestinationData data = AdtSdkTransportClient.buildDestinationData(system);
        ISystemConfiguration configuration = data.getSystemConfiguration();

        assertEquals(ISystemConfiguration.SNCType.SNC_HIGHEST_AVAILABLE, configuration.getSNCType());
    }
}
