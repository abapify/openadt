package org.openadt.bootstrap;

import org.openadt.config.SystemProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EclipseAdtDetector implements SystemDetector {
    private final List<Path> workspacePaths;

    public EclipseAdtDetector() {
        this(SetupPathLocator.eclipseWorkspacePaths());
    }

    EclipseAdtDetector(List<Path> workspacePaths) {
        this.workspacePaths = List.copyOf(workspacePaths);
    }

    @Override
    public List<SystemProfile> detect() {
        List<SystemProfile> systems = new ArrayList<>();
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
