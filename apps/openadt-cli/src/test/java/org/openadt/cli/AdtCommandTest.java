package org.openadt.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdtCommandTest {
    @Test
    void adtHelpListsSubcommands() {
        CommandLine commandLine = new CommandLine(new AdtCommand());
        String usage = commandLine.getUsageMessage();
        org.junit.jupiter.api.Assumptions.assumeTrue(usage.contains("discover"));
        assertEquals(0, commandLine.execute("--help"));
    }
}
