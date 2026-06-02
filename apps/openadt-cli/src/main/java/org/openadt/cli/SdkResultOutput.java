package org.openadt.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.openadt.config.CliLog;
import org.openadt.sap.adt.sdk.AdtTransportRequestRow;
import org.openadt.sap.adt.sdk.SdkDocumentResult;
import org.openadt.sap.adt.sdk.SdkJsonResult;
import org.openadt.sap.adt.sdk.SdkServiceResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Writes {@link SdkServiceResult} to stdout/files (shared by discovery, sdk, transports).
 */
final class SdkResultOutput {
    private static final ObjectMapper JSON =
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private SdkResultOutput() {
    }

    static void write(
        SdkServiceResult result,
        Path outFile,
        boolean jsonOutput,
        boolean full,
        Runnable printTextSummary
    ) throws IOException {
        if (result instanceof SdkDocumentResult document) {
            SdkDocumentOutput.write(
                document.body(),
                document.contentType(),
                outFile,
                jsonOutput,
                full,
                printTextSummary
            );
            return;
        }
        if (result instanceof SdkJsonResult json) {
            writeJson(json, outFile, jsonOutput, full, printTextSummary);
        }
    }

    private static void writeJson(
        SdkJsonResult result,
        Path outFile,
        boolean jsonOutput,
        boolean full,
        Runnable printTextSummary
    ) throws IOException {
        byte[] jsonBytes = JSON.writeValueAsBytes(result.payload());
        if (outFile != null) {
            if (outFile.getParent() != null) {
                Files.createDirectories(outFile.getParent());
            }
            Files.write(outFile, jsonBytes);
            CliLog.info("wrote " + jsonBytes.length + " bytes JSON to " + outFile.toAbsolutePath());
        }
        if (jsonOutput || outFile == null || full) {
            if (!jsonOutput && outFile == null) {
                printTextSummary.run();
            } else {
                CliLog.stdout().write(jsonBytes);
                CliLog.stdout().flush();
            }
        } else {
            printTextSummary.run();
        }
    }

    static void printTransportListSummary(SdkJsonResult result) {
        CliLog.stdout().println("destination: " + result.destinationId()
            + (result.fromEclipse() ? " (eclipse)" : " (config)"));
        CliLog.stdout().println("message: " + result.message());
        if (result.payload() instanceof java.util.Map<?, ?> map) {
            Object count = map.get("count");
            if (count != null) {
                CliLog.stdout().println("count: " + count);
            }
            Object transports = map.get("transports");
            if (transports instanceof java.util.List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof AdtTransportRequestRow row) {
                        CliLog.stdout().println(
                            row.number() + " " + nullToDash(row.targetSystem()) + " " + nullToDash(row.description())
                        );
                    }
                }
            }
        }
    }

    private static String nullToDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }
}
