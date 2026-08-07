package org.openadt.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupPathLocatorTest {
    /** Runs {@code body} with {@code os.name}/{@code user.home} pinned, then restores them. */
    private void withPlatform(String osName, Path home, Runnable body) {
        String originalOs = System.getProperty("os.name");
        String originalHome = System.getProperty("user.home");
        try {
            System.setProperty("os.name", osName);
            System.setProperty("user.home", home.toString());
            body.run();
        } finally {
            restore("os.name", originalOs);
            restore("user.home", originalHome);
        }
    }

    private void restore(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    @Test
    void includesStagedDevcontainerRuntimeRoots(@TempDir Path tempDir) throws Exception {
        String originalUserDir = System.getProperty("user.dir");
        Path stagedJcoDir = tempDir.resolve(".devcontainer/dist/jco");
        Path stagedSncDir = tempDir.resolve(".devcontainer/dist/snc");
        Files.createDirectories(stagedJcoDir);
        Files.createDirectories(stagedSncDir);

        try {
            System.setProperty("user.dir", tempDir.toString());

            assertTrue(SetupPathLocator.jcoJarRoots().contains(stagedJcoDir));
            assertTrue(SetupPathLocator.jcoNativeSearchRoots().contains(stagedJcoDir));
            assertTrue(SetupPathLocator.jcoNativeSearchRoots().contains(stagedSncDir));
            assertTrue(SetupPathLocator.sapcryptoCandidates().contains(stagedSncDir.resolve("libsapcrypto.so")));
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    @Test
    void findsP2PoolOnMacOs(@TempDir Path home) {
        withPlatform("Mac OS X", home, () ->
            assertTrue(
                SetupPathLocator.jcoJarRoots().contains(home.resolve(".p2/pool/plugins")),
                "macOS p2 pool must be searched for ADT/JCo bundles"
            )
        );
    }

    @Test
    void findsP2PoolOnLinux(@TempDir Path home) {
        withPlatform("Linux", home, () ->
            assertTrue(SetupPathLocator.jcoJarRoots().contains(home.resolve(".p2/pool/plugins")))
        );
    }

    @Test
    void findsP2PoolOnWindows(@TempDir Path home) {
        withPlatform("Windows 11", home, () ->
            assertTrue(SetupPathLocator.jcoJarRoots().contains(home.resolve(".p2/pool/plugins")))
        );
    }

    @Test
    void searchesP2AndEclipseRootsForNatives(@TempDir Path home) {
        withPlatform("Mac OS X", home, () -> {
            List<Path> roots = SetupPathLocator.jcoNativeSearchRoots();
            assertTrue(roots.contains(home.resolve(".p2")));
            assertTrue(roots.contains(home.resolve("eclipse")));
        });
    }

    @Test
    void offersMacSecureLoginCryptoLib(@TempDir Path home) {
        withPlatform("Mac OS X", home, () -> {
            List<Path> candidates = SetupPathLocator.sapcryptoCandidates();
            assertTrue(candidates.contains(
                Path.of("/Applications/Secure Login Client.app/Contents/MacOS/lib/libsapcrypto.dylib")
            ));
            assertTrue(candidates.contains(
                home.resolve("Applications/Secure Login Client.app/Contents/MacOS/lib/libsapcrypto.dylib")
            ));
        });
    }

    @Test
    void macSecureLoginPathsAreNotOfferedOnOtherPlatforms(@TempDir Path home) {
        withPlatform("Linux", home, () ->
            assertFalse(
                SetupPathLocator.sapcryptoCandidates().stream()
                    .anyMatch(path -> path.toString().contains("Secure Login Client.app"))
            )
        );
    }

    @Test
    void includesMacSecureLoginInstallRoot(@TempDir Path home) {
        withPlatform("Mac OS X", home, () ->
            assertTrue(SetupPathLocator.secureLoginInstallPaths()
                .contains(Path.of("/Applications/Secure Login Client.app")))
        );
    }

    @Test
    void discoversEclipseInstallerWorkspace(@TempDir Path home) {
        withPlatform("Mac OS X", home, () ->
            assertTrue(
                SetupPathLocator.eclipseWorkspacePaths().contains(home.resolve("eclipse/workspace")),
                "Eclipse Installer places the workspace at ~/eclipse/workspace"
            )
        );
    }

    @Test
    void stillDiscoversConventionalWorkspaces(@TempDir Path home) {
        withPlatform("Mac OS X", home, () -> {
            List<Path> roots = SetupPathLocator.eclipseWorkspacePaths();
            assertTrue(roots.contains(home.resolve("workspace")));
            assertTrue(roots.contains(home.resolve("eclipse-workspace")));
        });
    }
}
