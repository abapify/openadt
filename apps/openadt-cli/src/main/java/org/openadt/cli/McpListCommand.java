package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "list",
    description = "List active SAP ADT MCP endpoints (one store file per port)",
    mixinStandardHelpOptions = true
)
public class McpListCommand implements Callable<Integer> {
    @Option(names = "--json", description = "Machine-readable JSON only")
    private boolean json;

    @Parameters(arity = "0..*", description = "Additional args forwarded to launcher")
    private List<String> remainder = new ArrayList<>();

    @Override
    public Integer call() {
        return McpLauncherInvoker.invoke(
            "list",
            McpCommandSupport.launcherArgs(remainder)
                .flag("--json", json)
                .build()
        );
    }
}
