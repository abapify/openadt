package org.openadt.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Probes the SAP ADT frontend after browser SSO to capture {@code Set-Cookie} headers the CLI never sees in the browser.
 */
public final class HttpSapSessionWarmup {
    private HttpSapSessionWarmup() {
    }

    public static Map<String, String> probe(OpenAdtConfig config, SystemProfile system, String ticket) {
        if (system == null || system.getAdt() == null || system.getAdt().getDiscoveryUrl() == null) {
            return HttpSapCookieStore.empty();
        }
        URI target = warmupUri(system.getAdt().getDiscoveryUrl());
        if (target == null) {
            return HttpSapCookieStore.empty();
        }
        try {
            HttpClient client = HttpAdtTransportClient.buildHttpClientForWarmup(config, system);
            HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(30))
                .header("Cookie", HttpSapCookieStore.buildCookieHeader(ticket, system.getClient(), HttpSapCookieStore.empty()))
                .header("Accept", "application/atomsvc+xml")
                .GET()
                .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            Map<String, String> cookies = HttpSapCookieStore.fromSetCookieHeaders(response.headers());
            CliLog.httpSso(
                "SAP session warmup "
                    + target
                    + " → HTTP "
                    + response.statusCode()
                    + "; Set-Cookie names: "
                    + HttpSapCookieStore.describeNames(cookies)
            );
            return cookies;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            CliLog.httpSso("SAP session warmup interrupted: " + error.getMessage());
            return HttpSapCookieStore.empty();
        } catch (IOException error) {
            CliLog.httpSso("SAP session warmup failed: " + error.getMessage());
            return HttpSapCookieStore.empty();
        }
    }

    static URI warmupUri(String discoveryUrl) {
        String value = discoveryUrl.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        URI base = URI.create(value);
        String path = base.getPath() != null ? base.getPath() : "";
        if (path.contains("/sap/bc/adt")) {
            return base.resolve("/sap/bc/adt/discovery");
        }
        return base;
    }
}
