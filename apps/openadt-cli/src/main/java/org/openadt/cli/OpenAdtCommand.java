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
        CommandLine.HelpCommand.class
    }
)
public class OpenAdtCommand implements Runnable {
    public static void main(String[] args) {
        int exitCode = newCommandLine().execute(args);
        System.exit(exitCode);
    }

    private static CommandLine newCommandLine() {
        CommandLine commandLine = new CommandLine(new OpenAdtCommand());
        registerAdtSubcommand(commandLine);
        return commandLine;
    }

    private static void registerAdtSubcommand(CommandLine commandLine) {
        try {
            Class<?> adtCommand = Class.forName("org.openadt.cli.AdtCommand");
            commandLine.addSubcommand("adt", adtCommand);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            // Distribution build omits SDK-native adt commands.
        }
    }

    @Override
    public void run() {
        newCommandLine().usage(CliLog.stdout());
    }
}
