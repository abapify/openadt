package org.openadt.cli;

import picocli.CommandLine.Command;

@Command(
    name = "auth",
    mixinStandardHelpOptions = true,
    description = "Authentication profile and SDK logon (login saves default_profile for all commands)",
    subcommands = {
        AuthLoginCommand.class,
        AuthLogoutCommand.class,
        AuthStatusCommand.class,
    }
)
public class AuthCommand {
}
