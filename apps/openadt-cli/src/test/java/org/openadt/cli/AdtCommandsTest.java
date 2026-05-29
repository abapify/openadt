package org.openadt.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtCommandsTest {
    @Test
    void discoverRequiresSystemAlias() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new AdtDiscoverCommand());
        cmd.setErr(new PrintWriter(err, true));
        int exit = cmd.execute();
        assertEquals(2, exit);
    }

    @Test
    void adtSubcommandsListedInHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new AdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("--help");
        String help = out.toString();
        assertTrue(help.contains("discover"));
        assertTrue(help.contains("logon"));
        assertTrue(help.contains("logon-status"));
    }

    @Test
    void rootListsAdtCommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("--help");
        assertTrue(out.toString().contains("adt"));
    }

}
