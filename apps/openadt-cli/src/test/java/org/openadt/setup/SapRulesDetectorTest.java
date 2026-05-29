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

class SapRulesDetectorTest {
    @Test
    void extractsAdtHostAndClientFromSapRules(@TempDir Path tempDir) throws IOException {
        Path sapRules = tempDir.resolve("saprules.xml");
        Files.writeString(sapRules, """
            <SAP>
              <rules>
                <rule id="5">
                  <files>
                    <name>https://sap-dev-app.example.com:8001/sap/bc/adt</name>
                  </files>
                  <contexts>
                    <context>
                      <system>DEV</system>
                      <client>100</client>
                    </context>
                  </contexts>
                </rule>
              </rules>
            </SAP>
            """);

        SapRulesDetector detector = new SapRulesDetector(List.of(sapRules));

        List<SystemProfile> systems = detector.detect();

        assertEquals(1, systems.size());
        SystemProfile system = systems.get(0);
        assertEquals("DEV", system.getSystemId());
        assertEquals("100", system.getClient());
        assertNotNull(system.getAdt());
        assertEquals("sap-dev-app.example.com", system.getAdt().getAshost());
        assertEquals("https://sap-dev-app.example.com:8001/sap/bc/adt", system.getAdt().getDiscoveryUrl());
    }
}
