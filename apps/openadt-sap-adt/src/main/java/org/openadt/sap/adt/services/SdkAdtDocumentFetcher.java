package org.openadt.sap.adt.services;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtException;
import org.openadt.sap.adt.fallback.http.AdtAcceptHeaders;
import org.openadt.sap.adt.sdk.AdtDiscoveryDocument;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.AdtTransportFactory;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;

/**
 * Fetches an ADT resource body through {@code IStatelessSystemSession.sendRequest}
 * ({@link org.openadt.sap.adt.sdk.AdtSdkTransportClient}).
 */
final class SdkAdtDocumentFetcher {
    private SdkAdtDocumentFetcher() {
    }

    static AdtDiscoveryDocument fetchGet(SapAdtSessionContext context, String adtPath) {
        try {
            AdtTransportClient transport = AdtTransportFactory.create(context.config(), context.system());
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", AdtAcceptHeaders.defaultAcceptHeaderValue(adtPath));
            ProxyRequest request = new ProxyRequest("GET", adtPath, "HTTP/1.1", headers, null);
            CliLog.sdk("IStatelessSystemSession.sendRequest GET " + adtPath);
            ProxyResponse response = transport.execute(context.system(), request);
            byte[] body = response.body() != null ? response.body() : new byte[0];
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            String message = ok
                ? "document OK (" + body.length + " bytes)"
                : "HTTP " + response.statusCode() + " " + response.reasonPhrase();
            return new AdtDiscoveryDocument(
                ok,
                response.statusCode(),
                message,
                context.destinationId(),
                context.fromEclipse(),
                response.getHeader("Content-Type"),
                body
            );
        } catch (Exception error) {
            throw new OpenAdtException("SDK document fetch failed: " + error.getMessage(), error);
        }
    }
}
