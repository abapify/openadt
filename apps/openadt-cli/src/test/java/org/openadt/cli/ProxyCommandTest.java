package org.openadt.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyCommandTest {
    @Test
    void parseHostExtractsHostFromListenAddress() {
        assertEquals("127.0.0.1", ProxyCommand.parseHost("127.0.0.1:8079"));
        assertEquals("127.0.0.1", ProxyCommand.parseHost("127.0.0.1:0"));
        assertEquals("localhost", ProxyCommand.parseHost("localhost"));
    }
}
