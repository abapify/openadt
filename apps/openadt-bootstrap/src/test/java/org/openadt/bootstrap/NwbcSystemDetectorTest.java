package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openadt.config.SystemProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NwbcSystemDetectorTest {
    @Test
    void detectsSystemClientFromRecentConnections(@TempDir Path tempDir) throws IOException {
        Path recentsDir = tempDir.resolve("Recents");
        Files.createDirectories(recentsDir);
        Files.writeString(recentsDir.resolve("svc-dev_SAPBC.recents"), """
            <?xml version="1.0" encoding="utf-8"?>
            <recents>
              <recent connection="DEV [PUBLIC]" serviceId="svc-dev" client="100" connectionType="SAPGUI" url="http://sap.com/sap/bc/gui/sap/its/webgui;~sysid=DEV;~service=0000"/>
            </recents>
            """);

        NwbcSystemDetector detector = new NwbcSystemDetector(List.of(recentsDir));

        List<SystemProfile> systems = detector.detect();

        assertEquals(1, systems.size());
        SystemProfile system = systems.get(0);
        assertEquals("sap-business-client", system.getSource());
        assertEquals("DEV", system.getAlias());
        assertEquals("DEV", system.getSystemId());
        assertEquals("100", system.getClient());
    }
}
