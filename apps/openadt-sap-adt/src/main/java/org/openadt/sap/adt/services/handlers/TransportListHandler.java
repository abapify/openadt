package org.openadt.sap.adt.services.handlers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openadt.sap.adt.sdk.AdtTransportRequestRow;
import org.openadt.sap.adt.sdk.SdkJsonResult;
import org.openadt.sap.adt.sdk.SdkServiceArgs;
import org.openadt.sap.adt.sdk.SdkServiceResult;
import org.openadt.sap.adt.services.SapAdtSessionContext;
import org.openadt.sap.adt.services.SdkServiceHandler;
import org.openadt.sap.adt.services.TransportService;

/**
 * {@code transport.list} — {@link com.sap.adt.transport.IAdtTransportService#findTransports(String user, String trfunction)}.
 */
public final class TransportListHandler implements SdkServiceHandler {
    @Override
    public SdkServiceResult execute(SapAdtSessionContext context, SdkServiceArgs args) throws Exception {
        String user = resolveUser(context, args);
        String trFunction = args.getOrDefault("trfunction", TransportService.DEFAULT_TRFUNCTION);
        List<AdtTransportRequestRow> transports = new TransportService().findTransports(context, user, trFunction);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user", user);
        payload.put("trfunction", trFunction);
        payload.put("count", transports.size());
        payload.put("transports", transports);
        return new SdkJsonResult(
            true,
            "transport list OK (" + transports.size() + " entries)",
            context.destinationId(),
            context.fromEclipse(),
            payload
        );
    }

    private static String resolveUser(SapAdtSessionContext context, SdkServiceArgs args) {
        String user = args.get("user");
        if (user != null && !user.isBlank()) {
            return user.trim();
        }
        user = context.destinationData().getUser();
        if (user != null && !user.isBlank()) {
            return user.trim();
        }
        user = context.system().getUser();
        if (user != null && !user.isBlank()) {
            return user.trim();
        }
        return "";
    }
}
