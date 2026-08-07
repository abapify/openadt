package org.openadt.sap.adt.sdk;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginXmlExtensionPointsTest {
    private String rewrite(String pluginXml) throws IOException {
        byte[] result = PluginXmlExtensionPoints.rewrite(
            new ByteArrayInputStream(pluginXml.getBytes(StandardCharsets.UTF_8))
        );
        return new String(result, StandardCharsets.UTF_8);
    }

    @Test
    void keepsExtensionPointsAndDropsExtensions() throws Exception {
        // Trimmed from the real com.sap.adt.destinations plugin.xml.
        String result = rewrite("""
            <?xml version="1.0" encoding="UTF-8"?>
            <?eclipse version="3.4"?>
            <plugin>
               <extension-point id="logonListeners" name="Logon Listeners" schema="schema/logonListeners.exsd"/>
               <extension point="com.sap.adt.destinations.model.httpAuthenticationHandler">
                  <handler authenticationKind="kind" class="com.sap.adt.Handler"/>
               </extension>
            </plugin>
            """);

        assertTrue(result.contains("<extension-point id=\"logonListeners\""));
        assertTrue(result.contains("name=\"Logon Listeners\""));
        // The contribution must not survive: registering it activates Eclipse-only collaborators.
        assertFalse(result.contains("httpAuthenticationHandler"));
        assertFalse(result.contains("com.sap.adt.Handler"));
        assertFalse(result.contains("<extension "));
    }

    @Test
    void returnsEmptyWhenNoExtensionPointsDeclared() throws Exception {
        assertEquals("", rewrite("""
            <?xml version="1.0" encoding="UTF-8"?>
            <plugin>
               <extension point="some.other.point"><thing/></extension>
            </plugin>
            """));
    }

    @Test
    void returnsEmptyForEmptyPlugin() throws Exception {
        assertEquals("", rewrite("<plugin/>"));
    }

    @Test
    void keepsEveryDeclaredPoint() throws Exception {
        String result = rewrite("""
            <plugin>
               <extension-point id="one" name="One"/>
               <extension-point id="two" name="Two"/>
               <extension-point id="three"/>
            </plugin>
            """);

        assertEquals(3, result.split("<extension-point ", -1).length - 1);
        assertTrue(result.contains("id=\"three\""));
    }

    @Test
    void skipsPointsWithoutId() throws Exception {
        String result = rewrite("""
            <plugin>
               <extension-point name="No Id"/>
               <extension-point id="good"/>
            </plugin>
            """);

        assertEquals(1, result.split("<extension-point ", -1).length - 1);
        assertTrue(result.contains("id=\"good\""));
    }

    @Test
    void escapesAttributeValues() throws Exception {
        String result = rewrite("<plugin><extension-point id=\"a&amp;b\" name=\"x&quot;y\"/></plugin>");

        assertTrue(result.contains("id=\"a&amp;b\""));
        assertTrue(result.contains("name=\"x&quot;y\""));
    }

    @Test
    void producesParseableOutput() throws Exception {
        String result = rewrite("<plugin><extension-point id=\"one\" name=\"One\"/></plugin>");

        // Feeding the result back in must be stable, since the registry parses it.
        assertTrue(rewrite(result).contains("id=\"one\""));
    }

    @Test
    void rejectsMalformedXml() {
        assertThrows(IOException.class, () -> rewrite("<plugin><extension-point id=\"unclosed\"></plugin>"));
    }

    @Test
    void rejectsDoctypeDeclarations() {
        // XXE guard: a plugin.xml with a DOCTYPE must be refused rather than resolved.
        assertThrows(IOException.class, () -> rewrite(
            "<!DOCTYPE plugin [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><plugin>"
                + "<extension-point id=\"a\"/></plugin>"
        ));
    }
}
