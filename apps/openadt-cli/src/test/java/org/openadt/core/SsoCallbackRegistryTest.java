package org.openadt.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoCallbackRegistryTest {
    @TempDir
    Path tempHome;

    @Test
    void reportsStalePortWhenAnotherCallbackIsActive() {
        withHome(tempHome, () -> {
            SsoCallbackRegistry.markActive(URI.create("http://localhost:63363/adt/redirect"), 63363);
            String hint = SsoCallbackRegistry.stalePortHint(50705);
            assertTrue(hint.contains("50705"));
            assertTrue(hint.contains("63363"));
            SsoCallbackRegistry.clear();
        });
    }

  /** Registry reads user.home; tests redirect it to a temp directory. */
    private static void withHome(Path home, Runnable action) {
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            action.run();
        } finally {
            if (previous == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previous);
            }
        }
    }
}
