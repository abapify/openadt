/**
 * Agent-foundation verb stubs — temporary placeholders for the 18 verbs
 * listed in {@code specs/adt-agent.md} §5. Each stub produces a structured
 * {@code INTERNAL} envelope with {@code status: awaiting-sap-sdk-wiring}
 * so an agent (and the CLI/MCP) can detect the gap and tell it apart
 * from "verb unknown" or "object unsupported".
 *
 * <p>The stubs compile in the {@code distribution} profile because they
 * do not import any {@code com.sap.adt.*} types. The real implementations
 * (one per verb) live in follow-up PRs that match the same registry
 * ids and add the {@code com.sap.adt.*} imports — those files go in the
 * distribution profile's exclude list the same way
 * {@code TransportService} and {@code LogonService} do.</p>
 *
 * <p>The mapping of verb id → SAP SDK class is in
 * {@link AgentVerbStubs#registerAll()}. See
 * {@code plans/lsp-agent-foundation.md} T2..T20 for the per-verb
 * follow-up tasks.</p>
 */
package org.openadt.sap.adt.services.agent.stubs;
