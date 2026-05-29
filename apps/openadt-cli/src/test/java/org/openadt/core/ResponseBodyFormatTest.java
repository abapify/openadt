package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseBodyFormatTest {
    @Test
    void detectsJsonByContentType() {
        assertTrue(ResponseBodyFormat.isJson(
            Map.of("Content-Type", "application/json"),
            "{}".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void detectsXmlByContentType() {
        assertFalse(ResponseBodyFormat.isJson(
            Map.of("Content-Type", "application/atomsvc+xml"),
            "<app:service/>".getBytes(StandardCharsets.UTF_8)
        ));
        assertTrue(ResponseBodyFormat.isXml(
            Map.of("Content-Type", "application/atomsvc+xml"),
            "<app:service/>".getBytes(StandardCharsets.UTF_8)
        ));
    }

    @Test
    void detectsJsonByBodyPrefix() {
        assertTrue(ResponseBodyFormat.isJson(Map.of(), "{\"a\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void detectsXmlByBodyPrefix() {
        assertFalse(ResponseBodyFormat.isJson(Map.of(), "<?xml version=\"1.0\"?>".getBytes(StandardCharsets.UTF_8)));
        assertTrue(ResponseBodyFormat.isXml(Map.of(), "<?xml version=\"1.0\"?><root/>".getBytes(StandardCharsets.UTF_8)));
    }
}
