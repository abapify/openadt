package org.openadt.product.fetch;

import org.json.JSONObject;
import org.json.XML;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Converts an ADT discovery HTTP body to JSON that mirrors the XML structure
 * (elements, attributes as {@code @name}, repeated siblings as arrays).
 */
public final class DiscoveryDocumentJsonFormatter {
    private DiscoveryDocumentJsonFormatter() {
    }

    public static byte[] formatDocumentBody(String contentType, byte[] body) {
        if (body == null || body.length == 0) {
            return "{}".getBytes(StandardCharsets.UTF_8);
        }
        Map<String, String> headers = contentType != null && !contentType.isBlank()
            ? Map.of("Content-Type", contentType)
            : Map.of();
        try {
            if (ResponseBodyFormat.isJson(headers, body)) {
                String pretty = ResponseBodyFormatter.prettyPrintJson(new String(body, StandardCharsets.UTF_8));
                return pretty.getBytes(StandardCharsets.UTF_8);
            }
            if (ResponseBodyFormat.isXml(headers, body)) {
                String xml = new String(body, StandardCharsets.UTF_8);
                JSONObject root = XML.toJSONObject(xml, true);
                return root.toString(2).getBytes(StandardCharsets.UTF_8);
            }
            JSONObject envelope = new JSONObject();
            envelope.put("contentType", contentType != null ? contentType : "");
            envelope.put("encoding", "base64");
            envelope.put("data", Base64.getEncoder().encodeToString(body));
            return envelope.toString(2).getBytes(StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalArgumentException(
                "Failed to convert discovery XML to JSON: " + error.getMessage(),
                error
            );
        }
    }
}
