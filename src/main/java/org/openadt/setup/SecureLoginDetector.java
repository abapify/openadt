package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.List;

public class SecureLoginDetector implements SystemDetector {
    private static final String SLC_HUB_URL = "https://127.0.0.1:34443";
    private static final int TIMEOUT_MS = 2000;

    public record DetectionResult(boolean available, String url) {}

    public DetectionResult detectSecureLogin() {
        try {
            URL url = URI.create(SLC_HUB_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            // Any HTTP response (including 4xx) means the service is reachable
            boolean available = code > 0;
            return new DetectionResult(available, SLC_HUB_URL);
        } catch (IOException e) {
            return new DetectionResult(false, SLC_HUB_URL);
        }
    }

    @Override
    public List<SystemProfile> detect() {
        // SecureLoginDetector doesn't produce system profiles directly
        return List.of();
    }
}
