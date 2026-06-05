package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "print-config",
    description = "Emit Cursor mcpServers JSON from MCP endpoint store",
    mixinStandardHelpOptions = true
)
public class McpPrintConfigCommand implements Callable<Integer> {
    @Option(names = "--port", description = "MCP HTTP port (required when multiple servers are active)")
    private Integer port;

    @Option(names = "--json", description = "Machine-readable JSON only")
    private boolean json;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        return McpLauncherInvoker.invoke(
            "print-config",
            McpCommandSupport.launcherArgs(remainder)
                .option("--port", port)
                .flag("--json", json)
                .build()
        );
    }
}
