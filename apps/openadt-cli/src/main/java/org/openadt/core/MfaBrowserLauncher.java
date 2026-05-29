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
        if (tryDesktopBrowse(uri)) {
            return;
        }
        if (os.contains("win")) {
            throw new IOException(
                "Unable to open browser on Windows: Desktop.browse is unavailable or failed. "
                    + "Run OpenADT in an interactive desktop session."
            );
        }
        openViaOsShell(uri, os);
    }

    private static boolean tryDesktopBrowse(URI uri) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return false;
        }
        try {
            Desktop.getDesktop().browse(uri);
            return true;
        } catch (Exception error) {
            CliLog.error("[openadt sdk] Desktop.browse failed: " + error.getMessage());
            return false;
        }
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
