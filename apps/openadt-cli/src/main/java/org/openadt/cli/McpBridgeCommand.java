package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "bridge",
    description = "Attach stdio to an existing healthy MCP backend (no spawn)",
    mixinStandardHelpOptions = true
)
public class McpBridgeCommand implements Callable<Integer> {
    @Option(names = "--port", description = "Attach to backend on this port (default: only one active)")
    private Integer port;

    @Option(
        names = "--stdio",
        description = "Bridge stdio (required)",
        required = true
    )
    private boolean stdio;

    @Option(names = "--json", description = "Machine-readable output on stdout")
    private boolean json;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        return McpLauncherInvoker.invoke(
            "bridge",
            McpCommandSupport.launcherArgs(remainder)
                .option("--port", port)
                .flag("--stdio", stdio)
                .flag("--json", json)
                .build()
        );
    }
}
