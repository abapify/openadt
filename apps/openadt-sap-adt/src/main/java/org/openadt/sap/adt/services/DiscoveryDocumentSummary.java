package org.openadt.sap.adt.services;

import java.nio.charset.StandardCharsets;

/**
 * Lightweight summary of a large ADT Atom discovery document for console output.
 */
public final class DiscoveryDocumentSummary {
    private DiscoveryDocumentSummary() {
    }

    public static String summarize(byte[] body) {
        if (body == null || body.length == 0) {
            return "empty body";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        int collections = countOccurrences(text, "<app:collection");
        if (collections == 0) {
            collections = countOccurrences(text, "<collection");
        }
        int workspaces = countOccurrences(text, "<app:workspace");
        if (workspaces == 0) {
            workspaces = countOccurrences(text, "<workspace");
        }
        return "bytes="
            + body.length
            + ", collections~="
            + collections
            + ", workspaces~="
            + workspaces
            + " (use --out <file> for full Atom/XML)";
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
