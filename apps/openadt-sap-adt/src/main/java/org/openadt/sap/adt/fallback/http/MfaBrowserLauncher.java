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
        builder.start();
    }

    private static void openViaWindowsShell(URI uri) throws IOException {
        new ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-Command",
            "& { param($url) Start-Process -FilePath $url }",
            uri.toString()
        ).start();
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
