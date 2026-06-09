package org.openadt.cli.adt;

import picocli.CommandLine.Parameters;

/**
 * Single CLI subcommand that dispatches to any registered agent verb.
 *
 * <p>Usage:</p>
 * <pre>
 *   openadt adt agent <verbId> --config /path/to/openadt.toml \
 *     --param key=value [--param key=value ...] [--json]
 * </pre>
 *
 * <p>This avoids the need to hand-write 18 (or 26) CLI subcommand classes
 * — one per catalog verb. The verb list is the table in
 * {@code AgentVerbStubs#registerAll()} and is the single source of truth.</p>
 */
@picocli.CommandLine.Command(
    name = "agent",
    mixinStandardHelpOptions = true,
    description = "Dispatch to a registered agent verb by id (see specs/adt-agent.md)"
)
final class AgentDispatchCommand extends AdtAgentCommandSupport {

    @Parameters(index = "0", arity = "1", description = "Verb id, e.g. adt_atc_run_check")
    String verbId;

    @Override
    protected String serviceId() {
        return verbId;
    }

    @Override
    protected String systemAliasParam() {
        return null;
    }
}
