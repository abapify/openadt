package org.openadt.sap.adt.services.agent.stubs;

import org.openadt.sap.adt.services.agent.AgentService;
import org.openadt.sap.adt.services.agent.AgentServiceRegistry;

/**
 * The 18 agent-foundation verb stubs, registered in
 * {@link AgentServiceRegistry} at class-load time.
 *
 * <p>Each verb follows the same shape: a thin class that calls
 * {@link VerbStubs#stub(String, String)} with the verb id and the SAP SDK
 * class the real implementation should call. The mapping table here is the
 * single source of truth for the verb list — the CLI
 * ({@code org.openadt.cli.adt.AdtCommand.subcommands}) and the future
 * {@code openadt-mcp-agent} server both reference these ids.</p>
 *
 * <p>See {@code plans/lsp-agent-foundation.md} T2..T20 for the per-verb
 * follow-up tasks that replace each stub with the real SAP SDK call. The
 * mapping from each verb to its SDK class is the same one used in
 * {@code specs/lsp-implementation-plan.md} and {@code specs/adt-agent.md}.</p>
 */
public final class AgentVerbStubs {

    static {
        registerAll();
    }

    private AgentVerbStubs() {
    }

    /** Register all 18 stubs in {@link AgentServiceRegistry}. Idempotent. */
    public static void registerAll() {
        register("adt_atc_get_variants", "IAdtCheckFactory");
        register("adt_atc_run_check", "IAdtCheckFactory");
        register("adt_lock_object", "IAdtLockService");
        register("adt_unlock_object", "IAdtLockService");
        register("adt_get_lock_status", "IAdtLockService");
        register("adt_format_code", "IGenericPrettyPrinterService");
        register("adt_get_diagnostics", "ICheckService");
        register("adt_find_references", "IAdtRisQueryService");
        register("adt_toggle_version", "IAdtVersionToggleService");
        register("adt_check_transport_lock", "IAdtTransportService");
        register("adt_create_transport", "IAdtTransportService");
        register("adt_assign_transport", "IAdtTransportService");
        register("adt_quick_search", "IAdtRisQueryService");
        register("adt_get_inactive_objects", "IAdtObjectListService");
        register("adt_run_application", "IAbapApplicationConsoleRunService");
        register("adt_get_hover", "ICodeElementInformationBackendService");
        register("adt_document_symbols", "IAdtObjectStructureService");
        register("adt_search_transports", "IAdtTransportService");
        register("adt_search_transports_advanced", "IAdtTransportService");
        register("adt_get_coverage", "IAdtCoverageService");
        register("adt_load_statement_coverage", "IAdtCoverageService");
        register("adt_refresh_object", "IAdtRefreshService");
        register("adt_get_object_name", "IAdtUriService");
        register("adt_get_package_name", "IAdtUriService");
        register("adt_get_folder_uri", "IAdtUriService");
        register("adt_get_external_links", "IAdtUriService");
    }

    /** Test-only: drop all registrations and re-register from scratch. */
    public static void resetForTests() {
        AgentServiceRegistry.resetForTests();
        registerAll();
    }

    private static void register(String verbId, String sdkClass) {
        AgentService service = VerbStubs.stub(verbId, sdkClass);
        AgentServiceRegistry.register(verbId, service);
    }
}
