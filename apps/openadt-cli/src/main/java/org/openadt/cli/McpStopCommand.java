package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "stop",
    description = "Stop MCP backend(s) tracked in the endpoint store",
    mixinStandardHelpOptions = true
)
public class McpStopCommand implements Callable<Integer> {
    @Option(names = "--port", description = "Stop only the backend on this port (default: all)")
    private Integer port;

    @Option(names = "--json", description = "Machine-readable output on stdout")
    private boolean json;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        return McpLauncherInvoker.invoke(
            "stop",
            McpCommandSupport.launcherArgs(remainder)
                .option("--port", port)
                .flag("--json", json)
                .build()
        );
    }
}
