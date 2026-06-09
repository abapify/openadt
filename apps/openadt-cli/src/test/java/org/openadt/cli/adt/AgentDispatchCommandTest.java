package org.openadt.cli.adt;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openadt.cli.OpenAdtCommand;
import org.openadt.sap.adt.services.agent.AgentServiceRegistry;
import org.openadt.sap.adt.services.agent.stubs.AgentVerbStubs;

import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDispatchCommandTest {

    @BeforeAll
    static void ensureStubsRegistered() {
        AgentVerbStubs.registerAll();
    }

    @Test
    void rootHelpStillMentionsAdt() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("--help");
        assertTrue(out.toString().contains("adt"));
    }

    @Test
    void adtHelpListsAgentDispatchSubcommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("adt", "--help");
        String help = out.toString();
        assertTrue(help.contains("agent"), "adt --help should list the `agent` dispatch subcommand");
    }

    @Test
    void agentDispatchHelpMentionsVerbId() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CommandLine cmd = new CommandLine(new OpenAdtCommand());
        cmd.setOut(new PrintWriter(out, true));
        cmd.execute("adt", "agent", "--help");
        String help = out.toString();
        assertTrue(help.contains("verbId"), "agent --help should describe the verbId positional");
    }

    @Test
    void allCatalogVerbsAreRegistered() {
        var ids = AgentServiceRegistry.serviceIds();
        for (String verb : new String[] {
            "adt_atc_get_variants", "adt_atc_run_check",
            "adt_lock_object", "adt_unlock_object", "adt_get_lock_status",
            "adt_format_code", "adt_get_diagnostics", "adt_find_references",
            "adt_toggle_version",
            "adt_check_transport_lock", "adt_create_transport", "adt_assign_transport",
            "adt_quick_search",
            "adt_get_inactive_objects", "adt_run_application",
            "adt_get_hover", "adt_document_symbols",
            "adt_search_transports", "adt_search_transports_advanced",
            "adt_get_coverage", "adt_load_statement_coverage",
            "adt_refresh_object", "adt_get_object_name", "adt_get_package_name",
            "adt_get_folder_uri", "adt_get_external_links"
        }) {
            assertTrue(ids.contains(verb), "missing verb: " + verb);
        }
        assertFalse(ids.isEmpty());
    }
}
