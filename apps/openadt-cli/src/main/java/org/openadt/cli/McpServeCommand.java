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
        description = "Destination import: auto (default), adtls, gui, openadt, or none"
    )
    private String importFrom;

    @Option(names = "--no-gui", description = "Same as --import-from=none")
    private boolean noGui;

    @Option(names = "--json", description = "Machine-readable status on stdout")
    private boolean json;

    @Option(names = "--show-token", description = "Print full Bearer token on stdout")
    private boolean showToken;

    @Option(
        names = "--stdio",
        description = "Stdio MCP transport; default is shared (ensure + attach). Add --standalone for monolithic."
    )
    private boolean stdio;

    @Option(
        names = "--standalone",
        description = "Monolithic mode: own adt-lsc, kill on exit. Only valid with --stdio."
    )
    private boolean standalone;

    @Option(
        names = {"--verbose", "-v"},
        description = "LSP trace + adt-lsc -consoleLog to ~/.openadt/logs/mcp-serve.log"
    )
    private boolean verbose;

    @Option(names = "--log-file", description = "Debug log file (with --verbose or MCP_DEBUG=1)")
    private String logFile;

    @Option(names = "--logon-timeout", description = "Logon wait in seconds (default: 300)")
    private Integer logonTimeoutSeconds;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        if (standalone && !stdio) {
            System.err.println("--standalone requires --stdio");
            return 1;
        }
        return McpLauncherInvoker.invoke(
            "serve",
            McpCommandSupport.launcherArgs(remainder)
                .option("--port", port)
                .option("--workspace", workspace)
                .option("--destination", destination)
                .flag("--no-gui", noGui)
                .option("--import-from", noGui || importFrom == null ? null : importFrom)
                .flag("--json", json)
                .flag("--show-token", showToken)
                .flag("--stdio", stdio)
                .flag("--standalone", standalone)
                .flag("--verbose", verbose)
                .option("--log-file", logFile)
                .option("--logon-timeout", logonTimeoutSeconds)
                .build()
        );
    }
}
