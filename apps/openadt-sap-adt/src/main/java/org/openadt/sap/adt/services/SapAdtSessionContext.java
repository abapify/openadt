package org.openadt.sap.adt.services;

import com.sap.adt.destinations.model.IAuthenticationToken;
import com.sap.adt.destinations.model.IDestinationData;

import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.destination.SapDestinationResolver;
import org.openadt.sap.adt.sdk.SapSdkRuntime;

/**
 * Prepared SDK session inputs: runtime bootstrap, resolved destination, logon token.
 */
public record SapAdtSessionContext(
    OpenAdtConfig config,
    SystemProfile system,
    IDestinationData destinationData,
    IAuthenticationToken authenticationToken,
    boolean fromEclipse
) {
    public String destinationId() {
        return destinationData.getId();
    }

    public static SapAdtSessionContext open(OpenAdtConfig config, SystemProfile system) {
        SapSdkRuntime.prepare(config);
        SapDestinationResolver.ResolvedDestination resolved = SapDestinationResolver.resolve(system);
        return new SapAdtSessionContext(
            config,
            system,
            resolved.destinationData(),
            resolved.authenticationToken(),
            resolved.fromEclipse()
        );
    }
}
