package org.openadt.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Minimal client for the SAP Secure Login Client Local Security Hub (HTTPS REST on 127.0.0.1:34443).
 * Requires CORS {@code Origin}/{@code Referer} matching the configured Secure Login Server profile.
 */
public class SecureLoginHubClient {
    public static final String DEFAULT_HUB_URL = "https://127.0.0.1:34443";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String hubBaseUrl;
    private final String origin;
    private final String referer;

    public SecureLoginHubClient(OpenAdtConfig.SecureLoginConfig secureLogin) {
        this(
            secureLogin != null && secureLogin.getLocalSecurityHub() != null
                ? secureLogin.getLocalSecurityHub()
                : DEFAULT_HUB_URL,
            secureLogin != null ? secureLogin.getOrigin() : null,
            secureLogin != null ? secureLogin.getReferer() : null,
            createHttpClient(
                secureLogin != null && secureLogin.getLocalSecurityHub() != null
                    ? secureLogin.getLocalSecurityHub()
                    : DEFAULT_HUB_URL
            ),
            new ObjectMapper()
        );
    }

    SecureLoginHubClient(String hubBaseUrl, String origin, String referer, HttpClient httpClient, ObjectMapper objectMapper) {
        this.hubBaseUrl = normalizeHubBase(hubBaseUrl);
        this.origin = origin;
        this.referer = referer;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public boolean isReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(hubBaseUrl + "/"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public String webAdapterStatus(String profileId) throws IOException, InterruptedException {
        URI uri = URI.create(hubBaseUrl + "/slc3/api/status?profileid=" + profileId);
        HttpResponse<String> response = httpClient.send(
            hubRequest(uri).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) {
            throw new IOException("Secure Login hub status failed with HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode status = root.path("status");
        return status.isTextual() ? status.asText() : "UNKNOWN";
    }

    public void loginWebAdapter(String profileId) throws IOException, InterruptedException {
        loginWebAdapter(profileId, false);
    }

    public void loginWebAdapter(String profileId, boolean browserMonitor) throws IOException, InterruptedException {
        URI uri = URI.create(hubBaseUrl + "/slc3/api/login");
        java.util.Map<String, Object> clientConfig = new java.util.LinkedHashMap<>();
        clientConfig.put("browserMonitor", browserMonitor);
        clientConfig.put("inactivityTimeout", 0);
        clientConfig.put("keySize", 2048);
        clientConfig.put("profileSLWA", profileId);
        clientConfig.put("localport", 34443);
        clientConfig.put("autoLogOut", false);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("profileid", profileId);
        payload.put("clientConfig", clientConfig);
        String body = objectMapper.writeValueAsString(payload);
        HttpResponse<String> response = httpClient.send(
            hubRequest(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Secure Login hub login failed with HTTP " + response.statusCode());
        }
    }

    public void ensureWebAdapterLoggedIn(String profileId) throws IOException, InterruptedException {
        ensureWebAdapterLoggedIn(profileId, false);
    }

    public void ensureWebAdapterLoggedIn(String profileId, boolean browserMonitor) throws IOException, InterruptedException {
        ensureWebAdapterLoggedIn(profileId, browserMonitor, false);
    }

    public void ensureWebAdapterLoggedIn(String profileId, boolean browserMonitor, boolean forceLogin)
        throws IOException, InterruptedException {
        String status = webAdapterStatus(profileId);
        if (!forceLogin && "LOGGED_IN".equalsIgnoreCase(status)) {
            return;
        }
        loginWebAdapter(profileId, browserMonitor);
        status = webAdapterStatus(profileId);
        if (!"LOGGED_IN".equalsIgnoreCase(status)) {
            throw new IllegalStateException(
                "Secure Login Web Adapter profile is not LOGGED_IN (status=" + status + "). "
                    + webAdapterLoginHint(browserMonitor)
            );
        }
    }

    private static String webAdapterLoginHint(boolean browserMonitor) {
        if (browserMonitor) {
            return "Complete MFA in the browser window that Secure Login opened, then retry.";
        }
        return "Open SAP Secure Login Client and sign in to the Web Adapter profile first, "
            + "or retry with hub browser login enabled.";
    }

    private HttpRequest.Builder hubRequest(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30));
        if (origin != null && !origin.isBlank()) {
            builder.header("Origin", origin);
        }
        if (referer != null && !referer.isBlank()) {
            builder.header("Referer", referer);
        }
        return builder;
    }

    private static String normalizeHubBase(String hubBaseUrl) {
        return hubBaseUrl.endsWith("/") ? hubBaseUrl.substring(0, hubBaseUrl.length() - 1) : hubBaseUrl;
    }

    private static HttpClient createHttpClient(String hubBaseUrl) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
        if (isLoopbackHub(hubBaseUrl) && hubBaseUrl.startsWith("https://")) {
            try {
                builder.sslContext(trustLocalHub(hubBaseUrl));
            } catch (IllegalStateException error) {
                CliLog.error(
                    "Secure Login hub TLS pinning skipped ("
                        + error.getMessage()
                        + "). HTTPS hub calls will fail until the hub is reachable and OpenADT is restarted."
                );
            }
        }
        return builder.build();
    }

    static boolean isLoopbackHub(String hubBaseUrl) {
        try {
            String host = URI.create(normalizeHubBase(hubBaseUrl)).getHost();
            if (host == null || host.isBlank()) {
                return false;
            }
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception error) {
            return false;
        }
    }

    private static SSLContext trustLocalHub(String hubBaseUrl) {
        try {
            return pinnedHubContext(hubBaseUrl);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to initialize TLS for Secure Login hub: " + error.getMessage(), error);
        }
    }

    private static SSLContext pinnedHubContext(String hubBaseUrl) throws Exception {
        if (!isLoopbackHub(hubBaseUrl)) {
            throw new IllegalArgumentException("Secure Login hub TLS pinning requires a loopback URL: " + hubBaseUrl);
        }
        URI hubUri = URI.create(normalizeHubBase(hubBaseUrl));
        String host = hubUri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Secure Login hub URL must include a host: " + hubBaseUrl);
        }
        int port = hubUri.getPort() > 0 ? hubUri.getPort() : 443;
        X509Certificate hubCertificate = LoopbackHubTlsProbe.probeCertificate(port);
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("secure-login-hub", hubCertificate);
        TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
        return context;
    }
}
