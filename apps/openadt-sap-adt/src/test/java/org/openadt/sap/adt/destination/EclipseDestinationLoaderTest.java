package org.openadt.sap.adt.destination;

import com.sap.adt.destinations.model.IDestinationData;
import com.sap.adt.destinations.model.ISystemConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EclipseDestinationLoaderTest {
    @Test
    void mapsExampleDestinationProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("id", "DEV_100_developer_en");
        properties.setProperty("systemId", "DEV");
        properties.setProperty("client", "100");
        properties.setProperty("user", "DEVELOPER");
        properties.setProperty("language", "EN");
        properties.setProperty("messageServer", "dev-ms.example.com");
        properties.setProperty("messageServerService", "3600");
        properties.setProperty("group", "PUBLIC");
        properties.setProperty("partnerName", "p:CN=SAPServiceDEV");
        properties.setProperty("SSOEnabled", "1");
        properties.setProperty("SNCType", "9");

        IDestinationData data = new EclipseDestinationLoader().map(properties);
        ISystemConfiguration sys = data.getSystemConfiguration();

        assertEquals("DEV_100_developer_en", data.getId());
        assertEquals("DEVELOPER", data.getUser());
        assertEquals("100", data.getClient());
        assertEquals("EN", data.getLanguage());
        assertEquals("dev-ms.example.com", sys.getMessageServer());
        assertTrue(sys.isLoadBalancing());
        assertEquals("3600", sys.getMessageServerService());
        assertEquals("PUBLIC", sys.getGroup());
        assertEquals("p:CN=SAPServiceDEV", sys.getPartnerName());
        assertTrue(sys.isSSOEnabled());
        assertEquals(ISystemConfiguration.SNCType.SNC_HIGHEST_AVAILABLE, sys.getSNCType());
    }
}
