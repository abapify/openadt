package org.openadt.cli.adt;

import org.openadt.config.CliLog;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Parent of all {@code openadt adt <verb>} subcommands.
 *
 * <p>Per {@code specs/adt-agent.md} §3, every verb lives as a CLI subcommand
 * of {@code adt}, backed by a service registered in
 * {@code org.openadt.sap.adt.services.agent.AgentServiceRegistry}. New verbs
 * are added by appending their class to {@code subcommands = …} below; this
 * is a deliberate, grep-able list (no reflective discovery).</p>
 *
 * <p>As of T1 the registry is empty; verb subcommands are added by T2..T20.</p>
 */
@Command(
    name = "adt",
    mixinStandardHelpOptions = true,
    description = "OpenADT agent foundation: programmatic ABAP operations (specs/adt-agent.md)",
    subcommands = {
        // Filled by individual verb tasks. T1 ships no verb subcommands.
    }
)
public class AdtCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(CliLog.stdout());
    }
}
