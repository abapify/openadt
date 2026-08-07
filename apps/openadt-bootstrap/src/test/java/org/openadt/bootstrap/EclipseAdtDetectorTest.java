package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openadt.config.SystemProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EclipseAdtDetectorTest {
    @TempDir
    Path workspace;

    private Path writeDestination(String id, String contents) throws Exception {
        Path destinationDir = workspace.resolve(
            ".metadata/.plugins/org.eclipse.core.resources.semantic/.cache/" + id
        );
        Files.createDirectories(destinationDir);
        Path destinationFile = destinationDir.resolve(".destination.properties");
        Files.writeString(destinationFile, contents);
        return destinationFile;
    }

    @Test
    void mapsEclipseDestinationToSystemProfile() throws Exception {
        writeDestination(
            "DEV_100_developer_en",
            """
            id=DEV_100_developer_en
            systemId=DEV
            client=100
            user=DEVELOPER
            language=EN
            messageServer=dev-ms.example.com
            messageServerService=3600
            group=GROUP1
            partnerName=p\\:CN\\=SAPServiceDEV
            SNCType=9
            SSOEnabled=1
            """
        );

        List<SystemProfile> systems = new EclipseAdtDetector(List.of(workspace)).detect();

        assertEquals(1, systems.size());
        SystemProfile profile = systems.get(0);
        assertEquals("eclipse-adt", profile.getSource());
        assertEquals("DEV_100_developer_en", profile.getAlias());
        assertEquals("DEV", profile.getSystemId());
        assertEquals("100", profile.getClient());
        assertEquals("DEVELOPER", profile.getUser());
        assertEquals("EN", profile.getLanguage());
        assertTrue(profile.getDescription().contains("DEV"));

        SystemProfile.JcoConfig jco = profile.getJco();
        assertNotNull(jco);
        assertEquals("dev-ms.example.com", jco.getMshost());
        assertEquals("3600", jco.getMsserv());
        assertEquals("DEV", jco.getR3name());
        assertEquals("GROUP1", jco.getGroup());
        // Java properties escaping is resolved by Properties.load, so the colon/equals survive.
        assertEquals("p:CN=SAPServiceDEV", jco.getSncPartnername());
        assertEquals("1", jco.getSncMode());
        assertEquals("9", jco.getSncQop());
        assertEquals("1", jco.getSncSso());
    }

    @Test
    void mapsSsoDisabledDestination() throws Exception {
        writeDestination(
            "DEV_100_developer_en",
            """
            id=DEV_100_developer_en
            systemId=DEV
            client=100
            SSOEnabled=0
            """
        );

        List<SystemProfile> systems = new EclipseAdtDetector(List.of(workspace)).detect();

        assertEquals(1, systems.size());
        assertEquals("0", systems.get(0).getJco().getSncSso());
    }

    @Test
    void detectsEveryDestinationInWorkspace() throws Exception {
        writeDestination("AAA_100_dev_en", "id=AAA_100_dev_en\nsystemId=AAA\nclient=100\n");
        writeDestination("BBB_200_dev_en", "id=BBB_200_dev_en\nsystemId=BBB\nclient=200\n");

        List<SystemProfile> systems = new EclipseAdtDetector(List.of(workspace)).detect();

        assertEquals(2, systems.size());
        assertEquals(List.of("AAA", "BBB"), systems.stream().map(SystemProfile::getSystemId).sorted().toList());
    }

    @Test
    void skipsWorkspaceWithoutDestinations() throws Exception {
        Path settings = workspace.resolve(
            ".metadata/.plugins/org.eclipse.core.runtime/.settings/com.sap.adt.tools.core.prefs"
        );
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "connectionData=DEV_100_developer_en\n");

        // Preferences alone carry no host/client/SNC data, so they must not yield a placeholder profile.
        assertTrue(new EclipseAdtDetector(List.of(workspace)).detect().isEmpty());
    }

    @Test
    void skipsMissingWorkspace() {
        EclipseAdtDetector detector = new EclipseAdtDetector(List.of(workspace.resolve("missing")));
        assertTrue(detector.detect().isEmpty());
    }
}
