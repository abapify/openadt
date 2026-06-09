/**
 * OpenADT agent foundation — the core envelope, URI parser, throttle, and
 * service registry that every {@code openadt adt <verb>} verb shares.
 *
 * <p>Each verb is its own concrete class in a sub-package
 * ({@code agent.atc}, {@code agent.lock}, ...) and registers with
 * {@link AgentServiceRegistry} at class-load time. The CLI parent
 * {@code org.openadt.cli.adt.AdtCommand} and the future
 * {@code openadt-mcp-agent} server both look up verbs through this registry.</p>
 *
 * <p>See {@code specs/adt-agent.md} for the full contract
 * (verb list, JSON schemas, error codes, transport requirements).</p>
 */
package org.openadt.sap.adt.services.agent;
