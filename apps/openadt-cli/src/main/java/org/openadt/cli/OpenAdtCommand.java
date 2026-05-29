package org.openadt.cli;

import org.openadt.core.CliLog;

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
        CommandLine.HelpCommand.class
    }
)
public class OpenAdtCommand implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new OpenAdtCommand()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(CliLog.stdout());
    }
}
