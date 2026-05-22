package org.openadt.core;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/**
 * Opens the user's default browser for Secure Login / SAML MFA (headed, not headless).
 */
public final class MfaBrowserLauncher {
    private MfaBrowserLauncher() {
    }

    public static void open(String url) throws IOException {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("MFA browser URL is empty");
        }
        URI uri = URI.create(url.trim());
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            openWindows(uri);
            return;
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
                return;
            } catch (Exception error) {
                System.err.println("[openadt sdk] Desktop.browse failed: " + error.getMessage());
            }
        }
        openViaOsShell(uri, os);
    }

    private static void openWindows(URI uri) throws IOException {
        Process process = new ProcessBuilder("cmd", "/c", "start", "", uri.toString())
            .redirectErrorStream(true)
            .start();
        System.err.println("[openadt sdk] started browser via: cmd /c start " + uri);
        System.err.flush();
    }

    private static void openViaOsShell(URI uri, String os) throws IOException {
        ProcessBuilder builder;
        if (os.contains("mac")) {
            builder = new ProcessBuilder("open", uri.toString());
        } else {
            builder = new ProcessBuilder("xdg-open", uri.toString());
        }
        builder.start();
    }
}
