package org.openadt.sap.adt.services;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sap.adt.transport.AdtTransportServiceFactory;
import com.sap.adt.transport.IAdtTransportRequest;
import com.sap.adt.transport.IAdtTransportService;

import org.openadt.sap.adt.sdk.AdtTransportRequestRow;

/**
 * Typed CTS transport API ({@link IAdtTransportService#findTransports}).
 */
public final class TransportService {
    /** CTS trfunction query param; SAP rejects empty (K=workbench). */
    public static final String DEFAULT_TRFUNCTION = "K";

    public List<AdtTransportRequestRow> findTransports(SapAdtSessionContext context, String user, String trFunction)
        throws Exception {
        new LogonService().logon(context);
        IAdtTransportService service = AdtTransportServiceFactory.createTransportService(context.destinationId());
        String effectiveUser = user != null ? user : "";
        String effectiveFunction = trFunction != null && !trFunction.isBlank() ? trFunction : DEFAULT_TRFUNCTION;
        List<IAdtTransportRequest> requests = service.findTransports(effectiveUser, effectiveFunction);
        List<AdtTransportRequestRow> rows = new ArrayList<>();
        if (requests == null) {
            return rows;
        }
        for (IAdtTransportRequest request : requests) {
            rows.add(toRow(request));
        }
        return rows;
    }

    private static AdtTransportRequestRow toRow(IAdtTransportRequest request) {
        Date lastChanged = request.getLastChanged();
        String changed = lastChanged != null ? lastChanged.getTime().toString() : null;
        String uri = request.getUri() != null ? request.getUri().toString() : null;
        return new AdtTransportRequestRow(
            request.getRequestNumber(),
            request.getTargetSystem(),
            request.getDescription(),
            request.getUser(),
            uri,
            request.getRepositoryId(),
            changed
        );
    }
}
