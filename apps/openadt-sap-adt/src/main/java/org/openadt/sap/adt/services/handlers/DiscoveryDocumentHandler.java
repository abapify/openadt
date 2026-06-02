package org.openadt.sap.adt.services.handlers;

import org.openadt.sap.adt.sdk.AdtDiscoveryDocument;
import org.openadt.sap.adt.sdk.SdkDocumentResult;
import org.openadt.sap.adt.sdk.SdkServiceArgs;
import org.openadt.sap.adt.sdk.SdkServiceResult;
import org.openadt.sap.adt.services.DiscoveryService;
import org.openadt.sap.adt.services.SapAdtSessionContext;
import org.openadt.sap.adt.services.SdkServiceHandler;

/**
 * {@code discovery.document} — {@link DiscoveryService#fetchDiscoveryDocument}.
 */
public final class DiscoveryDocumentHandler implements SdkServiceHandler {
    @Override
    public SdkServiceResult execute(SapAdtSessionContext context, SdkServiceArgs args) {
        AdtDiscoveryDocument document = new DiscoveryService().fetchDiscoveryDocument(context);
        return new SdkDocumentResult(
            document.ok(),
            document.statusCode(),
            document.statusMessage(),
            document.destinationId(),
            document.fromEclipse(),
            document.contentType(),
            document.body()
        );
    }
}
