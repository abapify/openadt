package org.openadt.sap.adt.services;

import com.sap.adt.compatibility.discovery.AdtDiscoveryFactory;
import com.sap.adt.compatibility.discovery.IAdtDiscovery;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtException;

/**
 * Shared {@link IAdtDiscovery} bootstrap for typed discovery operations.
 */
final class SdkDiscoveryAccess {
    private SdkDiscoveryAccess() {
    }

    static String resourcePath() {
        return AdtDiscoveryFactory.RESOURCE_URI.getPath();
    }

    static IAdtDiscovery requireDiscovery(SapAdtSessionContext context) {
        new LogonService().logon(context);
        String destinationId = context.destinationId();
        CliLog.sdk("AdtDiscoveryFactory.createDiscovery(" + destinationId + ", RESOURCE_URI)");
        IAdtDiscovery discovery = AdtDiscoveryFactory.createDiscovery(
            destinationId,
            AdtDiscoveryFactory.RESOURCE_URI
        );
        if (discovery == null) {
            throw new OpenAdtException("Failed to create IAdtDiscovery for destination: " + destinationId);
        }
        return discovery;
    }

    static IStatus readStatus(IAdtDiscovery discovery) {
        return readDiscoveryStatus(discovery, new NullProgressMonitor());
    }

    /**
     * Some ADT plugin versions expose {@link IAdtDiscovery} without {@code getStatus}; skip when absent.
     */
    private static IStatus readDiscoveryStatus(IAdtDiscovery discovery, IProgressMonitor monitor) {
        try {
            return discovery.getStatus(monitor);
        } catch (AbstractMethodError | NoSuchMethodError error) {
            CliLog.sdk("IAdtDiscovery.getStatus unavailable, continuing: " + error.getMessage());
            return null;
        }
    }
}
