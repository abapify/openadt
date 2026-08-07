package org.openadt.sap.adt.sdk;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reduces an Eclipse {@code plugin.xml} to just its {@code <extension-point>} declarations.
 *
 * <p>Used by {@link EclipseRegistryBootstrap} to declare the extension points the ADT SDK looks up
 * without registering the {@code <extension>} contributions, which would activate Eclipse-only
 * collaborators. Kept free of Eclipse types so it compiles — and is testable — in builds that have no
 * SAP/Eclipse jars available.
 */
final class PluginXmlExtensionPoints {
    private static final String EXTENSION_POINT = "extension-point";
    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    private PluginXmlExtensionPoints() {
    }

    /**
     * @return a minimal {@code plugin.xml} declaring only the extension points, or {@code null} when the
     *     document declares none
     * @throws IOException if the document cannot be parsed
     */
    static byte[] rewrite(InputStream pluginXml) throws IOException {
        Document document = parse(pluginXml);
        Element root = document.getDocumentElement();
        if (root == null) {
            return null;
        }
        NodeList points = root.getElementsByTagName(EXTENSION_POINT);

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<plugin>\n");
        int declared = 0;
        for (int i = 0; i < points.getLength(); i++) {
            Element point = (Element) points.item(i);
            String id = point.getAttribute("id");
            if (id == null || id.isBlank()) {
                continue;
            }
            xml.append("  <extension-point id=\"").append(escape(id)).append('"');
            String name = point.getAttribute("name");
            if (name != null && !name.isBlank()) {
                xml.append(" name=\"").append(escape(name)).append('"');
            }
            xml.append("/>\n");
            declared++;
        }
        if (declared == 0) {
            return null;
        }
        xml.append("</plugin>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Document parse(InputStream pluginXml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Bundle XML is local, but never resolve anything external from it.
            factory.setFeature(DISALLOW_DOCTYPE, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(pluginXml);
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException malformed) {
            throw new IOException("cannot parse plugin.xml: " + malformed.getMessage(), malformed);
        }
    }

    private static String escape(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
