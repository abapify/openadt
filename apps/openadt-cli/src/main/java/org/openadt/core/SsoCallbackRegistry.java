package org.openadt.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/** Tracks the active browser SSO localhost callback while a CLI process is waiting for a ticket. */
final class SsoCallbackRegistry {
    private static final String FILE_NAME = "sso-callback.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SsoCallbackRegistry() {
    }

    static void markActive(URI callbackUrl, int port) {
        ActiveCallback active = new ActiveCallback();
        active.callbackUrl = callbackUrl.toString();
        active.port = port;
        active.pid = ProcessHandle.current().pid();
        active.startedAt = Instant.now().toString();
        try {
            Files.createDirectories(runtimeDir());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(runtimeFile().toFile(), active);
        } catch (IOException error) {
            CliLog.error("Warning: could not write SSO callback registry: " + error.getMessage());
        }
    }

    static void clear() {
        try {
            Files.deleteIfExists(runtimeFile());
        } catch (IOException error) {
            CliLog.error("Warning: could not clear SSO callback registry: " + error.getMessage());
        }
    }

    static Optional<ActiveCallback> readActive() {
        Path file = runtimeFile();
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(file.toFile(), ActiveCallback.class));
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    static String stalePortHint(int requestedPort) {
        Optional<ActiveCallback> active = readActive();
        if (active.isEmpty()) {
            return "No OpenADT callback is listening (expected while fetch/proxy is waiting for browser SSO). "
                + "Start a fresh fetch/proxy run and use only the callback URL printed in that terminal. "
                + "Do not reuse reentranceticket tabs or redirect URLs from earlier runs.";
        }
        ActiveCallback current = active.get();
        if (current.port == requestedPort) {
            long pid = current.pid;
            boolean running = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            if (!running) {
                return "Callback port " + requestedPort + " was registered by OpenADT pid " + pid
                    + ", but that process has already exited. Start a fresh fetch/proxy run.";
            }
            return "OpenADT pid " + pid + " is still waiting on " + current.callbackUrl
                + ". Keep that terminal open until the browser redirect completes.";
        }
        return "Port " + requestedPort + " is from an older OpenADT run. "
            + "The active callback is " + current.callbackUrl + " (pid " + current.pid + "). "
            + "Close stale browser tabs and use only the URL from the current terminal.";
    }

    private static Path runtimeDir() {
        return Path.of(System.getProperty("user.home"), ".openadt", "runtime");
    }

    private static Path runtimeFile() {
        return runtimeDir().resolve(FILE_NAME);
    }

    static final class ActiveCallback {
        public String callbackUrl;
        public int port;
        public long pid;
        public String startedAt;
    }
}
