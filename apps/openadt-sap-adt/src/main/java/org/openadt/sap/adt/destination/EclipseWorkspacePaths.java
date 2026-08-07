package org.openadt.sap.adt.destination;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class EclipseWorkspacePaths {
    private static final String DEFAULT_WORKSPACE_DIR = "workspace";

    private EclipseWorkspacePaths() {
    }

    public static List<Path> discoverWorkspaceRoots() {
        List<Path> roots = new ArrayList<>(eclipseWorkspacePaths());
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            Path workspace = Path.of(home, DEFAULT_WORKSPACE_DIR);
            if (Files.isDirectory(workspace) && roots.stream().noneMatch(workspace::equals)) {
                roots.add(0, workspace);
            }
        }
        return roots;
    }

    private static List<Path> eclipseWorkspacePaths() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            paths.add(Path.of(home, DEFAULT_WORKSPACE_DIR));
            paths.add(Path.of(home, "eclipse-workspace"));
            // Eclipse Installer (Oomph) default, e.g. ~/eclipse/java-latest-released alongside ~/eclipse/workspace
            paths.add(Path.of(home, "eclipse", DEFAULT_WORKSPACE_DIR));
        }
        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("eclipse-workspace"));
            paths.add(windowsHome.resolve("Documents").resolve(DEFAULT_WORKSPACE_DIR));
            paths.add(windowsHome.resolve("Documents/eclipse-workspace"));
        }
        return new ArrayList<>(paths);
    }

    private static List<Path> windowsUserHomes() {
        Set<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home", "");
        String userProfile = System.getenv("USERPROFILE");
        if (os.contains("win")) {
            if (userProfile != null && !userProfile.isBlank()) {
                paths.add(Path.of(userProfile));
            }
            if (!home.isBlank()) {
                paths.add(Path.of(home));
            }
        }
        if (!home.isBlank() && home.startsWith("/mnt/")) {
            paths.add(Path.of(home));
        }
        return new ArrayList<>(paths);
    }
}
