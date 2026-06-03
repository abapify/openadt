package org.openadt.sap.adt.services;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryDocumentSummaryTest {
    @Test
    void summarizesAtomCollections() {
        String xml = """
            <app:service><app:workspace/>
            <app:collection href="a"/><app:collection href="b"/>
            </app:service>
            """;
        String summary = DiscoveryDocumentSummary.summarize(xml.getBytes(StandardCharsets.UTF_8));
        assertTrue(summary.contains("collections~=2"));
        assertTrue(summary.contains("bytes="));
    }
}
