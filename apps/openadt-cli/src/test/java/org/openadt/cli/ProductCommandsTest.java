package org.openadt.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCommandsTest {
    @Test
    void rootListsAuthAndDiscovery() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("--help");
        String help = out.toString();
        assertTrue(help.contains("auth"));
        assertTrue(help.contains("discovery"));
        assertTrue(help.contains("transports"));
        assertTrue(help.contains("mcp"));
        assertFalse(help.contains("sdk"));
        assertFalse(help.lines().anyMatch(line -> line.trim().startsWith("adt ")));
    }

    @Test
    void mcpSubcommandsListedInHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("mcp", "--help");
        String help = out.toString();
        assertTrue(help.contains("serve"));
        assertTrue(help.contains("status"));
        assertTrue(help.contains("list"));
        assertTrue(help.contains("print-config"));
    }

    @Test
    void authSubcommandsListedInHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new AuthCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("--help");
        String help = out.toString();
        assertTrue(help.contains("login"));
        assertTrue(help.contains("logout"));
        assertTrue(help.contains("status"));
    }
}
