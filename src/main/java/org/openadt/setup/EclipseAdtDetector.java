package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EclipseAdtDetector implements SystemDetector {
    @Override
    public List<SystemProfile> detect() {
        List<SystemProfile> systems = new ArrayList<>();
        List<Path> workspacePaths = getEclipseWorkspacePaths();
        for (Path workspacePath : workspacePaths) {
            if (Files.isDirectory(workspacePath)) {
                try {
                    detectFromWorkspace(workspacePath, systems);
                } catch (IOException e) {
                    // Skip unreadable workspaces
                }
            }
        }
        return systems;
    }

    private List<Path> getEclipseWorkspacePaths() {
        List<Path> paths = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        paths.add(Path.of(home, "eclipse-workspace"));
        return paths;
    }

    private void detectFromWorkspace(Path workspace, List<SystemProfile> systems) throws IOException {
        Path adtPrefs = workspace.resolve(".metadata/.plugins/org.eclipse.core.runtime/.settings/com.sap.adt.tools.core.prefs");
        if (Files.exists(adtPrefs)) {
            String content = Files.readString(adtPrefs);
            parseAdtPreferences(content, systems);
        }
    }

    private void parseAdtPreferences(String content, List<SystemProfile> systems) {
        String[] lines = content.split("\\n");
        for (String line : lines) {
            if (line.contains("connectionData")) {
                // Simple heuristic: extract system info from ADT connection data
                SystemProfile profile = new SystemProfile();
                profile.setSource("eclipse-adt");
                profile.setDescription("Eclipse ADT connection");
                systems.add(profile);
                break;
            }
        }
    }
}
