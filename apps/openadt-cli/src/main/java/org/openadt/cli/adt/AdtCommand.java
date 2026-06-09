package org.openadt.cli.adt;

import org.openadt.config.CliLog;
import org.openadt.sap.adt.services.agent.stubs.AgentVerbStubs;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Parent of all {@code openadt adt <verb>} subcommands.
 *
 * <p>Per {@code specs/adt-agent.md} §3, every verb lives as a CLI subcommand
 * of {@code adt}, backed by a service registered in
 * {@code org.openadt.sap.adt.services.agent.AgentServiceRegistry} via
 * {@link AgentVerbStubs#registerAll()}. The static block below forces that
 * registration on first CLI invocation, so {@code openadt adt --help} shows
 * the full verb list (rendered as "Subcommands" by picocli) without each
 * verb having to be listed here as a hard-coded class array.</p>
 *
 * <p>Note: the {@code subcommands = { … }} array is intentionally empty
 * because the stubs do not need their own per-verb CLI classes — every
 * verb is dispatched by the {@code agent <verbId>} catch-all that uses
 * {@link AgentVerbStubs}. This keeps the verb list to one source of truth
 * (the table in {@link AgentVerbStubs#registerAll()}).</p>
 */
@Command(
    name = "adt",
    mixinStandardHelpOptions = true,
    description = "OpenADT agent foundation: programmatic ABAP operations (specs/adt-agent.md)",
    subcommands = {
        // Verb dispatch is handled by `agent <verbId>`; see AgentVerbStubs.
        AgentDispatchCommand.class
    }
)
public class AdtCommand implements Runnable {

    static {
        // Force-load the stubs registry so every verb is registered before
        // any CLI subcommand runs.
        AgentVerbStubs.registerAll();
    }

    @Override
    public void run() {
        new CommandLine(this).usage(CliLog.stdout());
    }
}
