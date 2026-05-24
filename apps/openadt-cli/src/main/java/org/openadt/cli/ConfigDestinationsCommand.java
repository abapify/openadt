package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
    name = "destinations",
    mixinStandardHelpOptions = true,
    description = "Manage destination entries in OpenADT config",
    subcommands = {ConfigDestinationsCreateCommand.class}
)
public class ConfigDestinationsCommand implements Callable<Integer> {
    @ParentCommand
    private ConfigCommand parent;

    @Override
    public Integer call() {
        return new picocli.CommandLine(this).execute("--help");
    }

    Path getConfigPath() {
        return parent.getConfigPath();
    }
}
