package org.openadt.cli;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openadt.config.CliLog;
import org.openadt.sap.adt.sdk.SdkServiceArgs;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "transports",
    mixinStandardHelpOptions = true,
    description = "CTS transport requests via SAP ADT SDK",
    subcommands = TransportsListCommand.class
)
public class TransportsCommand implements Runnable {
    @Override
    public void run() {
        new picocli.CommandLine(this).usage(CliLog.stdout());
    }
}

@Command(
    name = "list",
    mixinStandardHelpOptions = true,
    description = "List transport requests (IAdtTransportService.findTransports)"
)
final class TransportsListCommand extends SdkServiceCommandSupport {
    @Parameters(index = "0", arity = "0..1", description = "System alias (default: active session)")
    String systemAlias;

    @Option(names = "--user", description = "SAP user filter (default: destination user)")
    String user;

    @Option(names = "--trfunction", description = "CTS trfunction: K=workbench, W=customizing, T=copies, *=all", defaultValue = "K")
    String trfunction;

    @Override
    protected String serviceId() {
        return "transport.list";
    }

    @Override
    protected String systemAliasParam() {
        return systemAlias;
    }

    @Override
    protected SdkServiceArgs buildServiceArgs() {
        Map<String, String> params = new LinkedHashMap<>(parseServiceParams(serviceParams));
        if (user != null && !user.isBlank()) {
            params.put("user", user.trim());
        }
        if (trfunction != null && !trfunction.isBlank()) {
            params.put("trfunction", trfunction.trim());
        }
        return SdkServiceArgs.of(params);
    }
}
