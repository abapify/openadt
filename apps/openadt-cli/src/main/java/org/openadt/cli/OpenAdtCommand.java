package org.openadt.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "openadt",
    mixinStandardHelpOptions = true,
    version = "openadt 1.0.0-SNAPSHOT",
    description = "OpenADT - Open-source SAP ADT proxy tool",
    subcommands = {
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
        new CommandLine(this).usage(System.out);
    }
}
