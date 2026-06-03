package org.openadt.cli;

import java.util.concurrent.Callable;

import org.openadt.config.CliLog;
import org.openadt.sap.adt.sdk.AdtSdkServiceGateway;
import picocli.CommandLine.Command;

@Command(
    name = "sdk",
    mixinStandardHelpOptions = true,
    description = "Invoke registered SAP ADT SDK services (see specs/sdk-services.md)",
    subcommands = {SdkInvokeCommand.class, SdkListCommand.class}
)
public class SdkCommand implements Runnable {
    @Override
    public void run() {
        CliLog.stdout().println("Usage: openadt sdk list | openadt sdk invoke <service-id> [SYSTEM]");
    }
}

@Command(name = "list", description = "List registered SDK service ids")
final class SdkListCommand implements Callable<Integer> {
    @Override
    public Integer call() {
        for (String id : AdtSdkServiceGateway.listSdkServices()) {
            CliLog.stdout().println(id);
        }
        return 0;
    }
}
