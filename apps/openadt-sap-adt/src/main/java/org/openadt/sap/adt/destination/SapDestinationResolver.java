package org.openadt.sap.adt.destination;

import com.sap.adt.destinations.model.IDestinationData;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtSdkTransportClient;

import java.io.IOException;
import java.util.Optional;
/**
 * Resolves ADT destination data for fetch/proxy: prefer Eclipse {@code .destination.properties}
 * (same as IDE), else build from config {@link SystemProfile}.
 */
public final class SapDestinationResolver {
    private SapDestinationResolver() {
    }

    public static ResolvedDestination resolve(SystemProfile system) {
        String query = system.getSystemId() != null ? system.getSystemId() : system.getAlias();
        Optional<EclipseDestinationLocator.EclipseDestinationEntry> eclipse;
        try {
            eclipse = new EclipseDestinationLocator().find(query);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to scan Eclipse destinations for " + query, error);
        }
        if (eclipse.isPresent()) {
            try {
                IDestinationData data = new EclipseDestinationLoader().load(eclipse.get().destinationFile());
                return new ResolvedDestination(data, null, true);
            } catch (IOException error) {
                throw new IllegalStateException(
                    "Failed to load Eclipse destination: " + eclipse.get().destinationFile(),
                    error
                );
            }
        }
        IDestinationData data = AdtSdkTransportClient.buildDestinationData(system);
        return new ResolvedDestination(
            data,
            AdtSdkTransportClient.createAuthenticationTokenForSystem(system),
            false
        );
    }

    public record ResolvedDestination(
        IDestinationData destinationData,
        com.sap.adt.destinations.model.IAuthenticationToken authenticationToken,
        boolean fromEclipse
    ) {
    }
}
