package org.openadt.sap.adt.services.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentUriTest {

    @Test
    void parsesAbsoluteAdtUri() {
        AgentUri u = AgentUri.parseOrNull("/sap/bc/adt/oo/classes/zcl_foo");
        assertNotNull(u);
        assertNull(u.type());
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo", u.path());
        assertEquals("zcl_foo", u.name());
    }

    @Test
    void parsesTypedAbsoluteUri() {
        AgentUri u = AgentUri.parseOrNull("class:/sap/bc/adt/oo/classes/zcl_foo");
        assertNotNull(u);
        assertEquals("class", u.type());
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo", u.path());
    }

    @Test
    void parsesTypedShorthand() {
        AgentUri u = AgentUri.parseOrNull("class:zcl_foo");
        assertNotNull(u);
        assertEquals("class", u.type());
        // Falls back to the shorthand as the path; name derived from it.
        assertEquals("zcl_foo", u.name());
    }

    @Test
    void preservesQueryParameters() {
        AgentUri u = AgentUri.parseOrNull("/sap/bc/adt/oo/classes/zcl_foo?version=active&foo=bar");
        assertNotNull(u);
        assertEquals(2, u.query().size());
        assertEquals("active", u.query().get("version"));
        assertEquals("bar", u.query().get("foo"));
    }

    @Test
    void ignoresHttpSchemeAsType() {
        AgentUri u = AgentUri.parseOrNull("http://host:8000/sap/bc/adt/oo/classes/zcl_foo");
        assertNotNull(u);
        assertNull(u.type(), "http:// scheme must not be interpreted as a type prefix");
        assertEquals("/sap/bc/adt/oo/classes/zcl_foo", u.path());
    }

    @Test
    void returnsNullOnNullOrBlank() {
        assertNull(AgentUri.parseOrNull(null));
        assertNull(AgentUri.parseOrNull(""));
        assertNull(AgentUri.parseOrNull("   "));
    }

    @Test
    void returnsNullOnInvalidUri() {
        assertNull(AgentUri.parseOrNull("class:"));
    }
}
