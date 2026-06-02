package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "discovery",
    mixinStandardHelpOptions = true,
    description = "Fetch ADT discovery document (registered service discovery.document)"
)
public class DiscoveryCommand extends SdkServiceCommandSupport {
    @Parameters(index = "0", arity = "0..1", description = "System alias (default: active session from auth login)")
    String systemAlias;

    @Override
    protected String serviceId() {
        return "discovery.document";
    }

    @Override
    protected String systemAliasParam() {
        return systemAlias;
    }
}
