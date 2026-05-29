package org.openadt.cli;

import picocli.CommandLine.Command;

@Command(
    name = "adt",
    mixinStandardHelpOptions = true,
    description = "SAP ADT SDK diagnostics (discover, logon)",
    subcommands = {
        AdtDiscoverCommand.class,
        AdtLogonCommand.class,
        AdtLogonStatusCommand.class,
    }
)
public class AdtCommand {
}
