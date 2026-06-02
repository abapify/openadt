package org.openadt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "invoke",
    mixinStandardHelpOptions = true,
    description = "Run a registered SDK service by id (e.g. transport.list)"
)
public class SdkInvokeCommand extends SdkServiceCommandSupport {
    @Parameters(index = "0", description = "Service id (openadt sdk list)")
    String registeredServiceId;

    @Parameters(index = "1", arity = "0..1", description = "System alias (default: active session)")
    String systemAlias;

    @Override
    protected String serviceId() {
        return registeredServiceId;
    }

    @Override
    protected String systemAliasParam() {
        return systemAlias;
    }
}
