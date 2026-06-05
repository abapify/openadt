package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "status",
    description = "Probe SAP ADT MCP HTTP endpoint",
    mixinStandardHelpOptions = true
)
public class McpStatusCommand implements Callable<Integer> {
    @Option(names = "--port", description = "MCP HTTP port (required when multiple servers are active)")
    private Integer port;

    @Option(names = "--token", description = "Bearer token override (default: read from endpoint store)")
    private String token;

    @Option(names = "--json", description = "Machine-readable result")
    private boolean json;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        return McpLauncherInvoker.invoke(
            "status",
            McpCommandSupport.launcherArgs(remainder)
                .option("--port", port)
                .option("--token", token)
                .flag("--json", json)
                .build()
        );
    }
}
