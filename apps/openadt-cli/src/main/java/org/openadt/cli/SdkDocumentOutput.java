package org.openadt.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.openadt.config.CliLog;
import org.openadt.product.fetch.DiscoveryDocumentJsonFormatter;

/**
 * Shared stdout/file output for SDK document commands (discovery and future services).
 */
final class SdkDocumentOutput {
    private SdkDocumentOutput() {
    }

    static boolean wantsJsonOutput(boolean json, String format, Path outFile) {
        if (json || "json".equalsIgnoreCase(format)) {
            return true;
        }
        return outFile != null && outFile.toString().toLowerCase().endsWith(".json");
    }

    static void write(
        byte[] body,
        String contentType,
        Path outFile,
        boolean jsonOutput,
        boolean full,
        Runnable printTextSummary
    ) throws IOException {
        if (outFile != null) {
            if (jsonOutput) {
                writeJsonBodyOutFile(outFile, body, contentType);
            } else {
                writeRawOutFile(outFile, body);
            }
        }
        if (jsonOutput) {
            byte[] jsonBody = DiscoveryDocumentJsonFormatter.formatDocumentBody(contentType, body);
            if (outFile == null || full) {
                OutputStream out = CliLog.stdout();
                out.write(jsonBody);
                out.flush();
            }
        } else {
            printTextSummary.run();
            if (full && body != null && body.length > 0) {
                CliLog.stdout().print(new String(body, StandardCharsets.UTF_8));
                CliLog.stdout().println();
            }
        }
    }

    private static void writeJsonBodyOutFile(Path target, byte[] body, String contentType) throws IOException {
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        byte[] jsonBody = DiscoveryDocumentJsonFormatter.formatDocumentBody(contentType, body);
        Files.write(target, jsonBody);
        CliLog.info("wrote " + jsonBody.length + " bytes JSON (XML→JSON) to " + target.toAbsolutePath());
    }

    private static void writeRawOutFile(Path target, byte[] body) throws IOException {
        byte[] payload = body != null ? body : new byte[0];
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, payload);
        CliLog.info("wrote " + payload.length + " bytes to " + target.toAbsolutePath());
    }
}
