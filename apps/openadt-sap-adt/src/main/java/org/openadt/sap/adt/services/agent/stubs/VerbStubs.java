package org.openadt.sap.adt.services.agent.stubs;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openadt.sap.adt.services.agent.AgentError;
import org.openadt.sap.adt.services.agent.AgentErrorCode;
import org.openadt.sap.adt.services.agent.AgentResult;
import org.openadt.sap.adt.services.agent.AgentService;
import org.openadt.sap.adt.services.agent.AgentUri;

/**
 * Central helper for the agent-foundation verb stubs shipped in the umbrella
 * branch {@code feat/agent-foundation-verb-stubs} (plans/lsp-agent-foundation.md
 * T2..T20 collapsed). Each verb is a tiny class that delegates here.
 *
 * <p>Why a stub and not the real SAP SDK call? The SAP ATC, lock, format,
 * references, transport, hover, symbols, coverage and other bundles
 * ({@code com.sap.adt.atc.*}, {@code com.sap.adt.lock.*},
 * {@code com.sap.adt.formatting.*}, {@code com.sap.adt.tools.*},
 * {@code com.sap.adt.repository.*} ...) are not part of the {@code distribution}
 * profile and not on the cloud agent's classpath. A real implementation
 * would import those types and break the CI build. The stubs compile in
 * distribution, register with {@link
 * org.openadt.sap.adt.services.agent.AgentServiceRegistry}, and produce a
 * clear {@code INTERNAL} envelope with {@code status: awaiting-sap-sdk-wiring}
 * that the CLI/MCP can surface verbatim. The actual SDK wiring for each
 * verb is a small follow-up PR (one verb = one PR = ~200 LoC diff), as
 * documented in {@code specs/adt-agent.md} §10 and the per-verb follow-up
 * section of the plan.</p>
 */
public final class VerbStubs {

    private VerbStubs() {
    }

    /**
     * Build the stub {@link AgentService} for a single verb. The returned
     * service:
     * <ul>
     *   <li>validates the {@code uri} arg with {@link AgentUri#parseOrNull};
     *       missing or unparseable → {@link AgentErrorCode#INVALID_URI},</li>
     *   <li>otherwise returns {@link AgentErrorCode#INTERNAL} (the verb is
     *       registered, the envelope is structurally valid, but the real
     *       SDK call is not yet wired in this build) with a structured
     *       {@code data} payload describing the verb, the destination, the
     *       recognized args, and the planned SAP SDK class to wire.</li>
     * </ul>
     *
     * @param verbId           MCP tool id, e.g. {@code "adt_atc_run_check"}
     * @param sdkClass         the SAP SDK class the real implementation
     *                         should call (e.g. {@code "IAdtCheckFactory"}),
     *                         for the {@code plannedSdkClass} field in the
     *                         envelope.
     */
    public static AgentService stub(String verbId, String sdkClass) {
        return (destinationId, args) -> {
            String uri = args.get("uri");
            if (uri != null && !uri.isBlank() && AgentUri.parseOrNull(uri) == null) {
                return AgentResult.fail(new AgentError(
                    AgentErrorCode.INVALID_URI,
                    "uri is not a parseable ADT URI: " + uri
                ));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("verb", verbId);
            data.put("destination", destinationId);
            data.put("args", new LinkedHashMap<>(args));
            data.put("plannedSdkClass", sdkClass);
            data.put("status", "awaiting-sap-sdk-wiring");
            return AgentResult.fail(
                new AgentError(
                    AgentErrorCode.INTERNAL,
                    "Verb '" + verbId + "' is registered and the CLI/MCP surface "
                        + "is wired. Real SAP SDK wiring for " + sdkClass + " is "
                        + "tracked per-verb in plans/lsp-agent-foundation.md (T2..T20). "
                        + "This build profile (distribution) does not include the SAP "
                        + "plugins; the runtime call path is added in a follow-up PR."
                ),
                data
            );
        };
    }
}
