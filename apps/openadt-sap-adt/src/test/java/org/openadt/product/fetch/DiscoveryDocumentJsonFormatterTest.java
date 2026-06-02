package org.openadt.product.fetch;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveryDocumentJsonFormatterTest {
    @Test
    void convertsAtomXmlToJson() {
        String xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <app:service xmlns:app="http://www.w3.org/2007/app">
              <app:workspace>
                <app:collection href="/sap/bc/adt/example"/>
              </app:workspace>
            </app:service>
            """;
        byte[] json = DiscoveryDocumentJsonFormatter.formatDocumentBody(
            "application/atomsvc+xml",
            xml.getBytes(StandardCharsets.UTF_8)
        );
        String text = new String(json, StandardCharsets.UTF_8);
        assertTrue(text.trim().startsWith("{"), () -> text);
        assertTrue(text.contains("app:service"), () -> text);
        assertTrue(text.contains("app:collection"), () -> text);
        assertTrue(text.contains("href") && text.contains("/sap/bc/adt/example"), () -> text);
        assertTrue(!text.contains("bodySize"), () -> text);
    }
}
