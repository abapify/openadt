package org.openadt.sap.adt.fallback.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.openadt.config.AdtHttpFrontendUrls;
import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
/**
 * Server-side probe after browser SSO to capture {@code Set-Cookie} headers the CLI never sees in the browser.
 * May request {@code /sap/bc/adt/discovery} over HTTP with the reentrance ticket only — never opened in a browser tab.
 */
public final class HttpSapSessionWarmup {
    private HttpSapSessionWarmup() {
    }

    public static Map<String, String> probe(OpenAdtConfig config, SystemProfile system, String ticket) {
        String apiBase = system != null && system.getAdt() != null
            ? AdtHttpFrontendUrls.resolveAdtApiBase(system.getAdt())
            : null;
        if (apiBase == null || apiBase.isBlank()) {
            return HttpSapCookieStore.empty();
        }
        URI target = warmupUri(apiBase);
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
        value = AdtHttpPaths.withHttpsSchemeIfMissing(value);
        URI base;
        try {
            base = URI.create(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
        if (base.getScheme() == null
            || (!"http".equalsIgnoreCase(base.getScheme()) && !"https".equalsIgnoreCase(base.getScheme()))
            || base.getHost() == null
            || base.getHost().isBlank()) {
            return null;
        }
        String path = base.getPath() != null ? base.getPath() : "";
        if (path.isBlank() || "/".equals(path)) {
            return base.resolve(AdtHttpPaths.ADT_DISCOVERY);
        }
        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        if (AdtHttpPaths.ADT_ICF_ROOT.equalsIgnoreCase(normalized) || normalized.startsWith(AdtHttpPaths.ADT_ICF_ROOT + "/")) {
            return base.resolve(AdtHttpPaths.ADT_DISCOVERY);
        }
        return base;
    }
}
