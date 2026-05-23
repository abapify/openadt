package org.openadt.core;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Pretty-prints fetch response bodies when format is recognized. */
public final class ResponseBodyFormatter {
    private ResponseBodyFormatter() {
    }

    public static byte[] format(Map<String, String> headers, byte[] body) {
        if (body == null || body.length == 0) {
            return new byte[0];
        }
        if (ResponseBodyFormat.isJson(headers, body)) {
            return prettyPrintJson(new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        }
        if (ResponseBodyFormat.isXml(headers, body)) {
            return prettyPrintXml(body);
        }
        return body;
    }

    static String prettyPrintJson(String jsonText) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < jsonText.length(); i++) {
            char c = jsonText.charAt(i);
            if (c == '"' && (i == 0 || jsonText.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (inString) {
                sb.append(c);
            } else if (c == '{' || c == '[') {
                sb.append(c);
                sb.append('\n');
                indent++;
                sb.append("  ".repeat(indent));
            } else if (c == '}' || c == ']') {
                sb.append('\n');
                indent--;
                sb.append("  ".repeat(indent));
                sb.append(c);
            } else if (c == ',') {
                sb.append(c);
                sb.append('\n');
                sb.append("  ".repeat(indent));
            } else if (c == ':') {
                sb.append(": ");
            } else if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                sb.append(c);
            }
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        return sb.toString();
    }

    static byte[] prettyPrintXml(byte[] body) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(
                new StreamSource(new ByteArrayInputStream(body)),
                new StreamResult(out)
            );
            return out.toByteArray();
        } catch (Exception error) {
            return body;
        }
    }
}
