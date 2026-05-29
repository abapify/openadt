package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JCoDestinationFactoryTest {
    @Test
    void buildsDerivedSsoPropertiesFromDetectedProfile() {
        OpenAdtConfig.RuntimeConfig runtime = new OpenAdtConfig.RuntimeConfig();
        runtime.setSapcrypto("C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib\\sapcrypto.dll");

        SystemProfile system = new SystemProfile();
        system.setAlias("DEV");
        system.setSystemId("DEV");
        system.setClient("100");
        system.setUser("DEVELOPER");
        system.setLanguage("EN");

        SystemProfile.JcoConfig jco = new SystemProfile.JcoConfig();
        jco.setMshost("dev-ms.example.com");
        jco.setMsserv("3600");
        jco.setR3name("DEV");
        jco.setGroup("PUBLIC");
        jco.setSncMode("1");
        jco.setSncQop("9");
        jco.setSncPartnername("p:CN=SAPServiceDEV");
        jco.setSncSso("1");
        jco.setSticky("1");
        jco.setDenyInitialPassword("1");
        system.setJco(jco);

        SystemProfile.AdtConfig adt = new SystemProfile.AdtConfig();
        adt.setAshost("sap-dev-app.example.com");
        adt.setAuthenticationKind("sso");
        system.setAdt(adt);

        JCoDestinationFactory factory = new JCoDestinationFactory(
            JCoDestinationFactoryTest.class.getClassLoader(),
            runtime
        );

        Properties props = factory.buildProperties(system);

        assertEquals("DEV_100_developer_en", props.getProperty("jco.client.destination"));
        assertEquals("1", props.getProperty("jco.client.sticky"));
        assertEquals("1", props.getProperty("jco.client.deny_initial_password"));
        assertEquals("CONFIGURED_USER", props.getProperty("jco.destination.auth_type"));
        assertEquals("DEVELOPER", props.getProperty("jco.destination.userid"));
        assertEquals("C:\\Program Files\\SAP\\FrontEnd\\SecureLogin\\lib\\sapcrypto.dll",
            props.getProperty("jco.client.snc_lib"));
        assertEquals("sap-dev-app.example.com", props.getProperty("adt.ashost"));
        assertEquals("sso", props.getProperty("adt.jco.client.authenticationKind"));
        assertEquals("DEV_100_developer_en", props.getProperty("adt.jco.client.destination"));
        assertEquals("DEV", props.getProperty("adt.r3name"));
    }
}
