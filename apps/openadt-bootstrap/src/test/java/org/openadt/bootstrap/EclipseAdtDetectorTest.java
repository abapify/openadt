package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openadt.config.SystemProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EclipseAdtDetectorTest {
    @TempDir
    Path workspace;

    @Test
    void detectsConnectionMarkerInAdtPrefs() throws Exception {
        Path settings = workspace.resolve(
            ".metadata/.plugins/org.eclipse.core.runtime/.settings/com.sap.adt.tools.core.prefs"
        );
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "connectionData=DEV_100_developer_en\n");

        EclipseAdtDetector detector = new EclipseAdtDetector(List.of(workspace));
        List<SystemProfile> systems = detector.detect();

        assertEquals(1, systems.size());
        assertEquals("eclipse-adt", systems.get(0).getSource());
        assertTrue(systems.get(0).getDescription().contains("Eclipse"));
    }

    @Test
    void skipsMissingWorkspace() {
        EclipseAdtDetector detector = new EclipseAdtDetector(List.of(workspace.resolve("missing")));
        assertTrue(detector.detect().isEmpty());
    }
}
