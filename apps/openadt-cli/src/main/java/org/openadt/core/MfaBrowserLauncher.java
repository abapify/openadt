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
                CliLog.error("[openadt sdk] Desktop.browse failed: " + error.getMessage());
            }
        }
        openViaOsShell(uri, os);
    }

    private static void openWindows(URI uri) throws IOException {
        String uriString = uri.toString();
        if (uriString.contains("\"") || uriString.contains("&") || uriString.contains("|") || uriString.contains(">") || uriString.contains("<")) {
            throw new IllegalArgumentException("URI contains potentially unsafe characters for Windows shell: " + uriString);
        }
        new ProcessBuilder("cmd", "/c", "start", "", uriString)
            .redirectErrorStream(true)
            .start();
        CliLog.error("[openadt sdk] started browser via: cmd /c start " + uriString);
        CliLog.stderr().flush();
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
