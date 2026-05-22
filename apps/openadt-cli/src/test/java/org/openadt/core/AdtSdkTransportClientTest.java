package org.openadt.core;

import com.sap.adt.destinations.model.IDestinationData;
import com.sap.adt.destinations.model.ISystemConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AdtSdkTransportClientTest {
    @Test
    void defaultAcceptForSystemInformation() {
        String[] accept = invokeDefaultAccept("/sap/bc/adt/core/http/systeminformation");
        assertArrayEquals(
            new String[]{"application/vnd.sap.adt.core.http.systeminformation.v1+json"},
            accept
        );
    }

    @Test
    void defaultAcceptForGenericAdtPath() {
        String[] accept = invokeDefaultAccept("/sap/bc/adt/discovery");
        assertEquals(3, accept.length);
        assertEquals("application/atom+xml;type=feed", accept[0]);
    }

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

    private static String[] invokeDefaultAccept(String uri) {
        try {
            var method = AdtSdkTransportClient.class.getDeclaredMethod("defaultAccept", String.class);
            method.setAccessible(true);
            return (String[]) method.invoke(null, uri);
        } catch (ReflectiveOperationException error) {
            throw new RuntimeException(error);
        }
    }
}
