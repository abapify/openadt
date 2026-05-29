package org.openadt.sap.adt.services;

import com.sap.adt.compatibility.discovery.AdtDiscoveryFactory;
import com.sap.adt.compatibility.discovery.IAdtDiscovery;
import com.sap.adt.compatibility.discovery.IAdtDiscoveryCollectionMember;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import java.util.Arrays;
import java.util.List;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtException;
import org.openadt.sap.adt.sdk.AdtDiscoveryReport;

/**
 * SDK ADT discovery via {@link IAdtDiscovery} (not raw HTTP).
 */
public final class DiscoveryService {
    public AdtDiscoveryReport discover(SapAdtSessionContext context, String collectionUri, String categoryTerm) {
        LogonService logonService = new LogonService();
        if (!logonService.status(context).loggedOn()) {
            logonService.logon(context);
        }
        String destinationId = context.destinationId();
        IProgressMonitor monitor = new NullProgressMonitor();
        CliLog.sdk("AdtDiscoveryFactory.createDiscovery(" + destinationId + ", RESOURCE_URI)");
        IAdtDiscovery discovery = AdtDiscoveryFactory.createDiscovery(destinationId, AdtDiscoveryFactory.RESOURCE_URI);
        IStatus status = discovery.getStatus(monitor);
        if (status != null && !status.isOK()) {
            String message = status.getMessage() != null ? status.getMessage() : status.toString();
            return new AdtDiscoveryReport(
                false,
                message,
                destinationId,
                context.fromEclipse(),
                collectionUri,
                categoryTerm,
                null,
                List.of()
            );
        }
        if (collectionUri == null || collectionUri.isBlank() || categoryTerm == null || categoryTerm.isBlank()) {
            return new AdtDiscoveryReport(
                true,
                "discovery OK",
                destinationId,
                context.fromEclipse(),
                null,
                null,
                null,
                List.of()
            );
        }
        CliLog.sdk("discovery.getCollectionMember(" + collectionUri + ", " + categoryTerm + ")");
        IAdtDiscoveryCollectionMember member = discovery.getCollectionMember(collectionUri, categoryTerm, monitor);
        if (member == null) {
            throw new OpenAdtException("Discovery returned no member for collection=" + collectionUri
                + " category=" + categoryTerm);
        }
        String memberUri = member.getUri() != null ? member.getUri().toString() : null;
        List<String> accepted = member.getAcceptedContentTypes() != null
            ? Arrays.asList(member.getAcceptedContentTypes())
            : List.of();
        return new AdtDiscoveryReport(
            true,
            "discovery OK",
            destinationId,
            context.fromEclipse(),
            collectionUri,
            categoryTerm,
            memberUri,
            accepted
        );
    }
}
