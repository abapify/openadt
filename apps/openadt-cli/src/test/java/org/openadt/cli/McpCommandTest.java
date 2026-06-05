package org.openadt.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCommandTest {
    static boolean launcherPresentInTree() {
        Path repo = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && repo != null; i++) {
            if (Files.isRegularFile(repo.resolve("tools/sap-adt-mcp-launcher/src/main.ts"))) {
                return true;
            }
            repo = repo.getParent();
        }
        return false;
    }

    @Test
    @EnabledIf("launcherPresentInTree")
    void resolveLauncherMainFindsScriptFromWorkingTree() {
        Path resolved = McpLauncherInvoker.resolveLauncherMain();
        assertNotNull(resolved);
        assertTrue(Files.isRegularFile(resolved));
        assertTrue(resolved.toString().replace('\\', '/').contains("sap-adt-mcp-launcher"));
    }
}
