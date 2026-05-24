package org.openadt.setup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

final class SetupPathLocator {
    private static final String USER_HOME_PROPERTY = "user.home";
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String APPDATA_ENV = "APPDATA";
    private static final String SAP_COMMON = "Common";
    private static final String SAPUI_LANDSCAPE = "SAPUILandscape.xml";
    private static final String SAP_BUSINESS_CLIENT = "SAP Business Client";

    private SetupPathLocator() {
    }

    static List<Path> sapGuiLandscapeFiles() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        String home = System.getProperty(USER_HOME_PROPERTY, "");

        if (os.contains("win")) {
            String appData = System.getenv(APPDATA_ENV);
            if (appData != null && !appData.isBlank()) {
                paths.add(Path.of(appData, "SAP", SAP_COMMON, SAPUI_LANDSCAPE));
            }
            if (!home.isBlank()) {
                paths.add(Path.of(home, "AppData", "Roaming", "SAP", SAP_COMMON, SAPUI_LANDSCAPE));
            }
        } else if (os.contains("mac")) {
            paths.add(Path.of(home, "Library", "Application Support", "SAP", SAP_COMMON, SAPUI_LANDSCAPE));
        }

        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("AppData/Roaming/SAP/" + SAP_COMMON + "/" + SAPUI_LANDSCAPE));
        }

        return new ArrayList<>(paths);
    }

    static List<Path> logonServerCacheFiles() {
        List<Path> files = new ArrayList<>();
        for (Path windowsHome : windowsUserHomes()) {
            Path cacheDir = windowsHome.resolve("AppData/Roaming/SAP/LogonServerConfigCache");
            files.addAll(listXmlFiles(cacheDir));
        }
        return files;
    }

    static List<Path> sapBusinessClientPaths() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv(APPDATA_ENV);
            if (appData != null && !appData.isBlank()) {
                paths.add(Path.of(appData, "SAP", SAP_BUSINESS_CLIENT));
            }
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null && !programFiles.isBlank()) {
                paths.add(Path.of(programFiles, "SAP", "SAP Business Client"));
            }
        }

        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("AppData/Roaming/SAP/NWBC"));
        }
        for (Path programFilesRoot : windowsProgramFilesRoots()) {
            paths.add(programFilesRoot.resolve("SAP").resolve("NWBC800"));
            paths.add(programFilesRoot.resolve("SAP").resolve(SAP_BUSINESS_CLIENT));
        }
        return new ArrayList<>(paths);
    }

    static List<Path> nwbcRecentsDirectories() {
        List<Path> paths = new ArrayList<>();
        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("AppData/Roaming/SAP/NWBC/Recents"));
        }
        return paths;
    }

    static List<Path> eclipseWorkspacePaths() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String home = System.getProperty(USER_HOME_PROPERTY, "");
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

    static List<Path> jcoJarRoots() {
        List<Path> paths = new ArrayList<>();
        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve(".p2/pool/plugins"));
        }
        paths.add(stagedDevcontainerDistDir().resolve("jco"));
        return paths;
    }

    static List<Path> jcoNativeSearchRoots() {
        List<Path> paths = new ArrayList<>();
        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("Documents"));
            paths.add(windowsHome.resolve("AppData/Local"));
            paths.add(windowsHome.resolve("AppData/Roaming"));
            paths.add(windowsHome.resolve(".p2"));
            paths.add(windowsHome.resolve("ide-latest-released/eclipse"));
            paths.add(windowsHome.resolve("ide-2025-06/eclipse"));
        }
        Path stagedDist = stagedDevcontainerDistDir();
        paths.add(stagedDist.resolve("jco"));
        paths.add(stagedDist.resolve("snc"));
        return paths;
    }

    static List<Path> sapcryptoCandidates() {
        List<Path> paths = new ArrayList<>();
        for (Path programFilesRoot : windowsProgramFilesRoots()) {
            paths.add(programFilesRoot.resolve("SAP/FrontEnd/SecureLogin/lib/sapcrypto.dll"));
        }
        paths.add(stagedDevcontainerDistDir().resolve("snc/libsapcrypto.so"));
        return paths;
    }

    static List<Path> secureLoginInstallPaths() {
        List<Path> paths = new ArrayList<>();
        for (Path programFilesRoot : windowsProgramFilesRoots()) {
            paths.add(programFilesRoot.resolve("SAP/FrontEnd/SecureLogin"));
        }
        return paths;
    }

    static List<Path> sapRulesFiles() {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        String appData = System.getenv(APPDATA_ENV);
        if (os.contains("win") && appData != null && !appData.isBlank()) {
            paths.add(Path.of(appData, "SAP", "Common", "saprules.xml"));
        }
        for (Path windowsHome : windowsUserHomes()) {
            paths.add(windowsHome.resolve("AppData/Roaming/SAP/Common/saprules.xml"));
        }
        return new ArrayList<>(paths);
    }

    private static List<Path> windowsUserHomes() {
        Set<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        String home = System.getProperty(USER_HOME_PROPERTY, "");
        String userProfile = System.getenv("USERPROFILE");

        if (os.contains("win")) {
            addIfPresent(paths, userProfile);
            addIfPresent(paths, home);
        }

        if (isWsl()) {
            String userName = System.getProperty("user.name", "");
            if (!userName.isBlank()) {
                addIfPresent(paths, "/mnt/c/Users/" + userName);
            }
            Path usersRoot = Path.of("/mnt/c/Users");
            if (Files.isDirectory(usersRoot)) {
                try (Stream<Path> stream = Files.list(usersRoot)) {
                    stream
                        .filter(Files::isDirectory)
                        .filter(path -> path.getFileName().toString().equalsIgnoreCase(userName))
                        .forEach(paths::add);
                } catch (IOException ignored) {
                    // Best-effort discovery only.
                }
            }
        }

        return new ArrayList<>(paths);
    }

    private static List<Path> windowsProgramFilesRoots() {
        Set<Path> paths = new LinkedHashSet<>();
        String os = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            addIfPresent(paths, System.getenv("ProgramFiles"));
            addIfPresent(paths, System.getenv("ProgramFiles(x86)"));
        }
        if (isWsl()) {
            addIfPresent(paths, "/mnt/c/Program Files");
            addIfPresent(paths, "/mnt/c/Program Files (x86)");
        }
        return new ArrayList<>(paths);
    }

    private static boolean isWsl() {
        if (System.getenv("WSL_DISTRO_NAME") != null) {
            return true;
        }
        Path procVersion = Path.of("/proc/version");
        if (!Files.isRegularFile(procVersion)) {
            return false;
        }
        try {
            String version = Files.readString(procVersion).toLowerCase(Locale.ROOT);
            return version.contains("microsoft");
        } catch (IOException e) {
            return false;
        }
    }

    private static List<Path> listXmlFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml"))
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void addIfPresent(Set<Path> paths, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        Path path = Path.of(rawPath);
        if (Files.exists(path)) {
            paths.add(path);
        }
    }

    private static Path stagedDevcontainerDistDir() {
        return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().resolve(".devcontainer/dist");
    }
}
