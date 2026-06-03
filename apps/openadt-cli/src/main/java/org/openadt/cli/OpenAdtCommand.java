package org.openadt.cli;

import org.openadt.config.CliLog;

import picocli.CommandLine;
import picocli.CommandLine.Command;
@Command(
    name = "openadt",
    mixinStandardHelpOptions = true,
    versionProvider = OpenAdtVersionProvider.class,
    description = "OpenADT - Open-source SAP ADT proxy tool",
    subcommands = {
        ConfigCommand.class,
        SetupCommand.class,
        ProxyCommand.class,
        FetchCommand.class,
        AuthCommand.class,
        DiscoveryCommand.class,
        TransportsCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class OpenAdtCommand implements Runnable {
    public static void main(String[] args) {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    private static CommandLine newCommandLine() {
        return new CommandLine(new OpenAdtCommand());
    }

    @Override
    public void run() {
        newCommandLine().usage(CliLog.stdout());
    }
}
