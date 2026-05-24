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
            openWindows(uri);
            return;
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

    private static void openWindows(URI uri) throws IOException {
        String uriString = uri.toString();
        if (uriString.contains("\"") || uriString.contains("&") || uriString.contains("|") || uriString.contains(">") || uriString.contains("<")) {
            throw new IllegalArgumentException("URI contains potentially unsafe characters for Windows shell: " + uriString);
        }
        ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "start", "", uriString);
        builder.redirectErrorStream(true);
        constrainWindowsPath(builder);
        builder.start();
        CliLog.error("[openadt sdk] started browser via: cmd /c start " + uriString);
        CliLog.stderr().flush();
    }

    private static void constrainWindowsPath(ProcessBuilder builder) {
        String systemRoot = builder.environment().getOrDefault("SystemRoot", "C:\\Windows");
        builder.environment().put("PATH", systemRoot + "\\System32");
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
