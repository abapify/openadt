package org.openadt.cli;

import picocli.CommandLine.Command;

@Command(
    name = "mcp",
    mixinStandardHelpOptions = true,
    description = "Launch official SAP ADT MCP (requires SAP ADT VS Code extension)",
    subcommands = {
        McpServeCommand.class,
        McpStatusCommand.class,
        McpPrintConfigCommand.class,
        picocli.CommandLine.HelpCommand.class
    }
)
public class McpCommand {
}
