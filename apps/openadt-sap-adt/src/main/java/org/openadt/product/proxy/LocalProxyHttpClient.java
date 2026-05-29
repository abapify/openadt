package org.openadt.product.proxy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openadt.config.OpenAdtException;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;

/**
 * Sends ADT requests to a running {@code openadt proxy} over loopback HTTP.
 */
public final class LocalProxyHttpClient implements AdtTransportClient {
    private final LocalProxyRegistry.ProxyEndpoint endpoint;
    private final HttpClient httpClient;

    public LocalProxyHttpClient(LocalProxyRegistry.ProxyEndpoint endpoint) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
        try {
            URI uri = URI.create("http://" + endpoint.getHost() + ":" + endpoint.getPort() + request.uri());
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(5))
                .method(request.method(), bodyPublisher(request.body()));

            for (Map.Entry<String, String> header : request.headers().entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }

            if (endpoint.isBasicAuth()) {
                String password = System.getenv("OPENADT_PROXY_PASSWORD");
                if (password == null || password.isBlank()) {
                    throw new IllegalStateException(
                        "Local proxy requires basic auth. Set OPENADT_PROXY_PASSWORD or start proxy without --local-auth."
                    );
                }
                String username = endpoint.getUsername() != null ? endpoint.getUsername() : "openadt";
                String token = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8)
                );
                builder.header("Authorization", "Basic " + token);
            }

            HttpResponse<byte[]> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );
            return toProxyResponse(response);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new OpenAdtException("Local proxy request interrupted", error);
        } catch (IOException error) {
            throw new OpenAdtException("Local proxy request failed: " + error.getMessage(), error);
        }
    }

    private static HttpRequest.BodyPublisher bodyPublisher(byte[] body) {
        if (body == null || body.length == 0) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private static ProxyResponse toProxyResponse(HttpResponse<byte[]> response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, String.join(", ", values));
            }
        });
        String version = "HTTP/1.1";
        int status = response.statusCode();
        String reason = reasonPhrase(status);
        byte[] body = response.body() != null ? response.body() : new byte[0];
        return new ProxyResponse(version, status, reason, headers, body);
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "";
        };
    }
}
