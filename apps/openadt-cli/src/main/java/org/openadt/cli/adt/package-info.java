/**
 * CLI subcommands of {@code openadt adt <verb>}. The parent
 * {@link org.openadt.cli.adt.AdtCommand} is registered in
 * {@code OpenAdtCommand.subcommands} alongside the existing
 * {@code fetch}, {@code proxy}, {@code auth}, {@code discovery},
 * {@code transports}, {@code mcp} subcommands. Each verb is a
 * subclass of {@link org.openadt.cli.adt.AdtAgentCommandSupport}.
 *
 * <p>See {@code specs/adt-agent.md} for the full verb list and the
 * JSON envelope contract.</p>
 */
package org.openadt.cli.adt;
