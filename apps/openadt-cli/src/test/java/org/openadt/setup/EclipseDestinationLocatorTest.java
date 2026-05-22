package org.openadt.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EclipseDestinationLocatorTest {
    @TempDir
    Path tempDir;

    @Test
    void listsAndFindsDestinationBySystemId() throws Exception {
        Path cache = EclipseDestinationLocator.semanticCacheRoot(tempDir)
            .resolve("DEV_100_developer_en");
        Files.createDirectories(cache);
        Files.writeString(cache.resolve(EclipseDestinationLocator.DESTINATION_FILE), """
            id=DEV_100_developer_en
            systemId=DEV
            client=100
            user=DEVELOPER
            messageServer=dev-ms.example.com
            group=PUBLIC
            partnerName=p:CN=SAPServiceDEV
            SSOEnabled=1
            SNCType=9
            """);

        EclipseDestinationLocator locator = new EclipseDestinationLocator(List.of(tempDir));
        List<EclipseDestinationLocator.EclipseDestinationEntry> all = locator.listAll();
        assertEquals(1, all.size());
        assertEquals("DEV_100_developer_en", all.get(0).id());

        Optional<EclipseDestinationLocator.EclipseDestinationEntry> bySid =
            locator.find("DEV");
        assertTrue(bySid.isPresent());
        assertEquals("DEV_100_developer_en", bySid.get().id());

        Optional<EclipseDestinationLocator.EclipseDestinationEntry> byId =
            locator.find("DEV_100_developer_en");
        assertTrue(byId.isPresent());
        assertEquals("DEV_100_developer_en", byId.get().id());
    }
}
