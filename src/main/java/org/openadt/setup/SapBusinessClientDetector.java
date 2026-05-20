package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SapBusinessClientDetector implements SystemDetector {
    @Override
    public List<SystemProfile> detect() {
        List<SystemProfile> systems = new ArrayList<>();
        List<Path> configPaths = getConfigPaths();
        for (Path path : configPaths) {
            if (Files.exists(path)) {
                SystemProfile profile = new SystemProfile();
                profile.setSource("sap-business-client");
                profile.setDescription("SAP Business Client detected at " + path);
                systems.add(profile);
                break;
            }
        }
        return systems;
    }

    private List<Path> getConfigPaths() {
        List<Path> paths = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                paths.add(Path.of(appData, "SAP", "SAP Business Client"));
            }
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                paths.add(Path.of(programFiles, "SAP", "SAP Business Client"));
            }
        }
        return paths;
    }
}
