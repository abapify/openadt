package org.openadt.sap.adt.services;

import com.sap.adt.compatibility.discovery.IAdtDiscovery;
import com.sap.adt.compatibility.discovery.IAdtDiscoveryCollectionMember;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import java.util.Arrays;
import java.util.List;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtException;
import org.openadt.sap.adt.sdk.AdtDiscoveryDocument;
import org.openadt.sap.adt.sdk.AdtDiscoveryReport;

/**
 * ADT discovery: {@link IAdtDiscovery} for status and collection members; document bytes via
 * {@code IStatelessSystemSession.sendRequest} on {@code AdtDiscoveryFactory.RESOURCE_URI}
 * (same SDK stack as {@code openadt fetch}).
 */
public final class DiscoveryService {

    /**
     * Validates {@link IAdtDiscovery#getStatus()} then returns the discovery Atom/XML document
     * from the SDK session. {@link IAdtDiscovery} does not expose the raw document API.
     */
    public AdtDiscoveryDocument fetchDiscoveryDocument(SapAdtSessionContext context) {
        IAdtDiscovery discovery = SdkDiscoveryAccess.requireDiscovery(context);
        IStatus status = SdkDiscoveryAccess.readStatus(discovery);
        if (status != null && !status.isOK()) {
            String message = status.getMessage() != null ? status.getMessage() : status.toString();
            return new AdtDiscoveryDocument(
                false,
                0,
                message,
                context.destinationId(),
                context.fromEclipse(),
                null,
                new byte[0]
            );
        }
        String path = SdkDiscoveryAccess.resourcePath();
        CliLog.sdk("IAdtDiscovery OK; fetching RESOURCE_URI document via SDK session");
        return SdkAdtDocumentFetcher.fetchGet(context, path);
    }

    public AdtDiscoveryReport discover(SapAdtSessionContext context, String collectionUri, String categoryTerm) {
        IAdtDiscovery discovery = SdkDiscoveryAccess.requireDiscovery(context);
        IStatus status = SdkDiscoveryAccess.readStatus(discovery);
        if (status != null && !status.isOK()) {
            String message = status.getMessage() != null ? status.getMessage() : status.toString();
            return new AdtDiscoveryReport(
                false,
                message,
                context.destinationId(),
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
                context.destinationId(),
                context.fromEclipse(),
                null,
                null,
                null,
                List.of()
            );
        }
        IProgressMonitor monitor = new NullProgressMonitor();
        CliLog.sdk("IAdtDiscovery.getCollectionMember(" + collectionUri + ", " + categoryTerm + ")");
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
            context.destinationId(),
            context.fromEclipse(),
            collectionUri,
            categoryTerm,
            memberUri,
            accepted
        );
    }
}
