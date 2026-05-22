package org.openadt.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpAdtTransportClient implements AdtTransportClient {
    private static final String DEFAULT_VERSION = "HTTP/1.1";
    private static final String WELL_KNOWN_INFO_CONTENT_TYPE = "application/vnd.com.sap.adt.wellknowninfo.v1+json";
    private final HttpClient httpClient;
    private final AdtHttpCookieProvider cookieProvider;
    private final OpenAdtConfig config;
    private final ObjectMapper objectMapper;

    public HttpAdtTransportClient(OpenAdtConfig config) {
        this(
            config,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
            new AdtHttpCookieProvider(),
            new ObjectMapper()
        );
    }

    HttpAdtTransportClient(OpenAdtConfig config, HttpClient httpClient, AdtHttpCookieProvider cookieProvider, ObjectMapper objectMapper) {
        this.config = config;
        this.httpClient = httpClient;
        this.cookieProvider = cookieProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
        try {
            URI targetUri = buildTargetUri(system, request.uri());
            HttpRequest httpRequest = buildHttpRequest(system, request, targetUri);
            HttpResponse<byte[]> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

            Map<String, String> headers = new LinkedHashMap<>();
            response.headers().map().forEach((key, values) -> headers.put(key, String.join(", ", values)));

            return new ProxyResponse(
                DEFAULT_VERSION,
                response.statusCode(),
                reasonPhrase(response.statusCode()),
                headers,
                response.body()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute HTTP ADT call: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing HTTP ADT call", e);
        }
    }

    HttpRequest buildHttpRequest(SystemProfile system, ProxyRequest request, URI targetUri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri)
            .timeout(Duration.ofSeconds(60));

        request.headers().forEach(builder::header);
        builder.header("Cookie", buildCookieHeader(system));

        byte[] body = request.body() != null ? request.body() : new byte[0];
        if (body.length == 0) {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(request.method(), HttpRequest.BodyPublishers.ofByteArray(body));
        }
        return builder.build();
    }

    URI buildTargetUri(SystemProfile system, String requestUri) {
        URI baseUri = discoverAdtApiBase(system);
        URI relative = URI.create(requestUri);
        return baseUri.resolve(relative);
    }

    String buildCookieHeader(SystemProfile system) {
        String mysapsso2 = cookieProvider.resolveMysapsso2(config, system);

        List<String> cookies = new java.util.ArrayList<>();
        cookies.add("MYSAPSSO2=" + mysapsso2);
        if (system.getClient() != null && !system.getClient().isBlank()) {
            cookies.add("sap-usercontext=sap-client=" + system.getClient());
        }
        return String.join("; ", cookies);
    }

    private URI discoverAdtApiBase(SystemProfile system) {
        if (system.getAdt() == null || system.getAdt().getDiscoveryUrl() == null || system.getAdt().getDiscoveryUrl().isBlank()) {
            throw new IllegalStateException(
                "HTTP ADT transport requires destinations.<alias>.adt.discovery_url to be configured with a logical frontend URL."
            );
        }

        URI configuredUri = normalizeBaseUri(system.getAdt().getDiscoveryUrl());
        URI originUri = originUri(configuredUri);

        URI wellKnownUri = originUri.resolve("/.well-known/sap-adt-info");
        String wellKnownApi = readApiUrlFromWellKnown(system, wellKnownUri);
        if (wellKnownApi != null) {
            return normalizeBaseUri(wellKnownApi);
        }

        URI virtualHostUri = originUri.resolve("/sap/public/bc/icf/virtualhost");
        String virtualHostApi = readApiUrlFromVirtualHost(system, virtualHostUri);
        if (virtualHostApi != null) {
            return normalizeBaseUri(virtualHostApi);
        }

        if (configuredUri.getPath() != null && configuredUri.getPath().contains("/sap/bc/adt")) {
            return configuredUri;
        }

        throw new IllegalStateException(
            "Unable to resolve an ADT API URL from discovery_url. Configure a logical frontend that exposes /.well-known/sap-adt-info or /sap/public/bc/icf/virtualhost."
        );
    }

    private URI normalizeBaseUri(String rawBase) {
        String value = rawBase;
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        URI uri = URI.create(value);
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
        return URI.create(uri.getScheme() + "://" + uri.getAuthority() + path);
    }

    private URI originUri(URI uri) {
        return URI.create(uri.getScheme() + "://" + uri.getAuthority() + "/");
    }

    private String readApiUrlFromWellKnown(SystemProfile system, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", WELL_KNOWN_INFO_CONTENT_TYPE)
                .header("Cookie", buildCookieHeader(system))
                .GET()
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode serviceEndpoint = root.path("resource").path("service_endpoint");
            return serviceEndpoint.isTextual() ? serviceEndpoint.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String readApiUrlFromVirtualHost(SystemProfile system, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Cookie", buildCookieHeader(system))
                .GET()
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode api = root.path("relatedUrls").path("api");
            return api.isTextual() ? api.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String reasonPhrase(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 202 -> "Accepted";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 409 -> "Conflict";
            case 412 -> "Precondition Failed";
            case 415 -> "Unsupported Media Type";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> Integer.toString(statusCode);
        };
    }
}
