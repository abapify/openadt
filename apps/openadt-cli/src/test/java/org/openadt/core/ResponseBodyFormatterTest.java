package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseBodyFormatterTest {
    @Test
    void prettyPrintsJson() {
        byte[] formatted = ResponseBodyFormatter.format(
            Map.of("Content-Type", "application/json"),
            "{\"a\":1}".getBytes(StandardCharsets.UTF_8)
        );
        String text = new String(formatted, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"a\""));
        assertTrue(text.indexOf('\n') >= 0);
    }

    @Test
    void prettyPrintsJsonWithEscapedQuotes() {
        byte[] formatted = ResponseBodyFormatter.format(
            Map.of("Content-Type", "application/json"),
            "{\"msg\":\"value with \\\\\"quote\\\"\"}".getBytes(StandardCharsets.UTF_8)
        );
        String text = new String(formatted, StandardCharsets.UTF_8);
        assertTrue(text.contains("\"msg\""));
    }

    @Test
    void prettyPrintsXml() {
        byte[] formatted = ResponseBodyFormatter.format(
            Map.of("Content-Type", "application/xml"),
            "<root><item/></root>".getBytes(StandardCharsets.UTF_8)
        );
        String text = new String(formatted, StandardCharsets.UTF_8);
        assertTrue(text.contains("<root>"));
        assertTrue(text.indexOf('\n') >= 0);
    }
}
