package org.openadt.sap.adt.destination;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class EclipseWorkspacePaths {
    private EclipseWorkspacePaths() {
    }

    static List<Path> discoverWorkspaceRoots() {
        List<Path> roots = new ArrayList<>(eclipseWorkspacePaths());
        String home = System.getProperty("user.home", "");
        if (!home.isBlank()) {
            Path workspace = Path.of(home, "workspace");
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
            paths.add(Path.of(home, "workspace"));
            paths.add(Path.of(home, "eclipse-workspace"));
        }
        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("eclipse-workspace"));
            paths.add(windowsHome.resolve("Documents/workspace"));
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
