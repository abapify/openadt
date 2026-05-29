package org.openadt.sap.adt.fallback.http;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.openadt.config.CliLog;

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
            openViaWindowsShell(uri);
            return;
        }
        if (isWsl()) {
            openViaWindowsShell(uri);
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

    private static void openViaOsShell(URI uri, String os) throws IOException {
        ProcessBuilder builder;
        if (os.contains("mac")) {
            builder = new ProcessBuilder("open", uri.toString());
        } else {
            builder = new ProcessBuilder("xdg-open", uri.toString());
        }
        applyTrustedPath(builder, os);
        builder.start();
    }

    private static void openViaWindowsShell(URI uri) throws IOException {
        ProcessBuilder builder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toString());
        applyTrustedPath(builder, "windows");
        builder.start();
    }

    private static void applyTrustedPath(ProcessBuilder builder, String os) {
        if (os != null && os.contains("win")) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot == null || systemRoot.isBlank()) {
                systemRoot = "C:\\Windows";
            }
            builder.environment().put("PATH", systemRoot + "\\System32;" + systemRoot);
            return;
        }
        if (os != null && os.contains("mac")) {
            builder.environment().put("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
            return;
        }
        builder.environment().put("PATH", "/usr/bin:/bin");
    }

    private static boolean isWsl() {
        if (System.getenv("WSL_DISTRO_NAME") != null || System.getenv("WSL_INTEROP") != null) {
            return true;
        }
        try {
            String version = Files.readString(Path.of("/proc/version")).toLowerCase(Locale.ROOT);
            return version.contains("microsoft") || version.contains("wsl");
        } catch (IOException ignored) {
            return false;
        }
    }
}
