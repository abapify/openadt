package org.openadt.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openadt.core.SystemProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SapGuiLandscapeDetectorTest {
    @Test
    void detectsLoadBalancedSapGuiSystems(@TempDir Path tempDir) throws IOException {
        Path landscapeFile = tempDir.resolve("SAPUILandscape.xml");
        Files.writeString(landscapeFile, """
            <?xml version="1.0"?>
            <Landscape version="1">
              <Messageservers>
                <Messageserver uuid="ms-dev" name="DEV" description="Example Development" host="dev-ms.example.com" port="3600"/>
              </Messageservers>
              <Services>
                <Service type="SAPGUI" uuid="svc-dev" name="DEV [PUBLIC]" systemid="DEV" msid="ms-dev" server="PUBLIC" sncname="p:CN=SAPServiceDEV" sncop="9"/>
              </Services>
            </Landscape>
            """);

        SapGuiLandscapeDetector detector = new SapGuiLandscapeDetector(List.of(landscapeFile));

        List<SystemProfile> systems = detector.detect();

        assertEquals(1, systems.size());
        SystemProfile system = systems.get(0);
        assertEquals("DEV", system.getAlias());
        assertEquals("sapgui", system.getSource());
        assertEquals("Example Development", system.getDescription());
        assertEquals("DEV", system.getSystemId());
        assertNotNull(system.getJco());
        assertEquals("dev-ms.example.com", system.getJco().getMshost());
        assertEquals("3600", system.getJco().getMsserv());
        assertEquals("DEV", system.getJco().getR3name());
        assertEquals("PUBLIC", system.getJco().getGroup());
        assertEquals("1", system.getJco().getSncMode());
        assertEquals("9", system.getJco().getSncQop());
        assertEquals("p:CN=SAPServiceDEV", system.getJco().getSncPartnername());
        assertEquals("1", system.getJco().getSncSso());
    }
}
