package org.openadt.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Help-text assertions for the new {@code openadt adt} subcommand added by the
 * agent-foundation rollout. Kept in its own file so the lead's T1 PR can
 * ship without touching the pre-existing {@code ProductCommandsTest}.
 */
class AdtCommandHelpTest {

    @Test
    void rootHelpMentionsAdtSubcommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("--help");
        String help = out.toString();
        assertTrue(help.contains("adt"),
            "root --help should list the 'adt' subcommand from the agent foundation");
    }

    @Test
    void adtSubcommandHelp() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("adt", "--help");
        String help = out.toString();
        assertTrue(help.contains("agent foundation"),
            "openadt adt --help should describe itself as the agent foundation surface");
    }
}
