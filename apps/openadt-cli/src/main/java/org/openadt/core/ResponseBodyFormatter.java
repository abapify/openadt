package org.openadt.core;

import javax.xml.XMLConstants;
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

    private static boolean isUnescapedQuote(String jsonText, int quoteIndex) {
        int backslashes = 0;
        for (int i = quoteIndex - 1; i >= 0 && jsonText.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 0;
    }

    static String prettyPrintJson(String jsonText) {
        StringBuilder sb = new StringBuilder();
        JsonPrettyState state = new JsonPrettyState();
        for (int i = 0; i < jsonText.length(); i++) {
            appendPrettyJsonChar(jsonText, i, sb, state);
        }
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void appendPrettyJsonChar(String jsonText, int index, StringBuilder sb, JsonPrettyState state) {
        char c = jsonText.charAt(index);
        if (c == '"' && isUnescapedQuote(jsonText, index)) {
            state.inString = !state.inString;
            sb.append(c);
            return;
        }
        if (state.inString) {
            sb.append(c);
            return;
        }
        if (c == '{' || c == '[') {
            sb.append(c).append('\n');
            state.indent++;
            sb.append("  ".repeat(state.indent));
            return;
        }
        if (c == '}' || c == ']') {
            sb.append('\n');
            state.indent--;
            sb.append("  ".repeat(state.indent));
            sb.append(c);
            return;
        }
        if (c == ',') {
            sb.append(c).append('\n').append("  ".repeat(state.indent));
            return;
        }
        if (c == ':') {
            sb.append(": ");
            return;
        }
        if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
            sb.append(c);
        }
    }

    private static final class JsonPrettyState {
        private int indent;
        private boolean inString;
    }

    static byte[] prettyPrintXml(byte[] body) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
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
