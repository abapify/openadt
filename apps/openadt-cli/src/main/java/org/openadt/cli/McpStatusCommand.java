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
    @Option(names = "--port", description = "MCP HTTP port (default: 2236)")
    private Integer port;

    @Option(names = "--token", description = "Bearer token for probe")
    private String token;

    @Option(names = "--json", description = "Machine-readable result")
    private boolean json;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        List<String> args = new ArrayList<>();
        if (port != null) {
            args.add("--port");
            args.add(String.valueOf(port));
        }
        if (token != null) {
            args.add("--token");
            args.add(token);
        }
        if (json) {
            args.add("--json");
        }
        args.addAll(remainder);
        return McpLauncherInvoker.invoke("status", args.toArray(String[]::new));
    }
}
