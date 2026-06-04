package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "serve",
    description = "Start SAP ADT language server and MCP HTTP endpoint",
    mixinStandardHelpOptions = true
)
public class McpServeCommand implements Callable<Integer> {
    @Option(names = "--port", description = "MCP HTTP port (default: 2236)")
    private Integer port;

    @Option(names = "--workspace", description = "adt-lsc workspace directory")
    private String workspace;

    @Option(names = "--destination", description = "Active SAP destination id (optional)")
    private String destination;

    @Option(
        names = "--import-from",
        description = "Destination import: gui (default) or none"
    )
    private String importFrom;

    @Option(names = "--no-gui", description = "Same as --import-from=none")
    private boolean noGui;

    @Option(names = "--json", description = "Machine-readable status on stdout")
    private boolean json;

    @Option(names = "--show-token", description = "Print full Bearer token on stdout")
    private boolean showToken;

    @Option(
        names = {"--verbose", "-v"},
        description = "LSP trace + adt-lsc -consoleLog to ~/.openadt/logs/mcp-serve.log"
    )
    private boolean verbose;

    @Option(names = "--log-file", description = "Debug log file (with --verbose or MCP_DEBUG=1)")
    private String logFile;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        List<String> args = new ArrayList<>();
        if (port != null) {
            args.add("--port");
            args.add(String.valueOf(port));
        }
        if (workspace != null) {
            args.add("--workspace");
            args.add(workspace);
        }
        if (destination != null) {
            args.add("--destination");
            args.add(destination);
        }
        if (noGui) {
            args.add("--no-gui");
        } else if (importFrom != null) {
            args.add("--import-from");
            args.add(importFrom);
        }
        if (json) {
            args.add("--json");
        }
        if (showToken) {
            args.add("--show-token");
        }
        if (verbose) {
            args.add("--verbose");
        }
        if (logFile != null) {
            args.add("--log-file");
            args.add(logFile);
        }
        args.addAll(remainder);
        return McpLauncherInvoker.invoke("serve", args.toArray(String[]::new));
    }
}
