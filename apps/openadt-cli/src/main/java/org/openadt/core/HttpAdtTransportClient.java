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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class HttpAdtTransportClient implements AdtTransportClient {
    private static final String DEFAULT_VERSION = "HTTP/1.1";
    private static final String HEADER_COOKIE = "Cookie";
    private static final String WELL_KNOWN_INFO_CONTENT_TYPE = "application/vnd.com.sap.adt.wellknowninfo.v1+json";
    private final HttpClient fixedHttpClient;
    private final ConcurrentHashMap<String, HttpClient> httpClientsByTrust = new ConcurrentHashMap<>();
    private final AdtHttpCookieProvider cookieProvider;
    private final OpenAdtConfig config;
    private final ObjectMapper objectMapper;
    private volatile String cachedMysapsso2;
    private final ThreadLocal<Boolean> resolveUsedDiskCache = new ThreadLocal<>();

    public HttpAdtTransportClient(OpenAdtConfig config) {
        this(config, null, new AdtHttpCookieProvider(), new ObjectMapper());
    }

    HttpAdtTransportClient(OpenAdtConfig config, HttpClient httpClient, AdtHttpCookieProvider cookieProvider, ObjectMapper objectMapper) {
        this.config = config;
        this.fixedHttpClient = httpClient;
        this.cookieProvider = cookieProvider;
        this.objectMapper = objectMapper;
    }

    private HttpClient httpClientFor(SystemProfile system) {
        if (fixedHttpClient != null) {
            return fixedHttpClient;
        }
        String key = HttpTlsTrustResolver.trustCacheKey(config, system, System::getenv);
        return httpClientsByTrust.computeIfAbsent(key, ignored -> buildHttpClient(config, system));
    }

    static HttpClient buildHttpClientForWarmup(OpenAdtConfig config, SystemProfile system) {
        return buildHttpClient(config, system);
    }

    private static HttpClient buildHttpClient(OpenAdtConfig config, SystemProfile system) {
        HttpTlsConfigurer tlsConfigurer = new HttpTlsConfigurer();
        javax.net.ssl.SSLContext sslContext = tlsConfigurer.buildSslContext(config, system);
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    @Override
    public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
        try {
            ProxyResponse response = sendOnce(system, request);
            if (response.statusCode() == 401) {
                boolean usedDiskCache = Boolean.TRUE.equals(resolveUsedDiskCache.get());
                invalidateTicket(system);
                if (usedDiskCache) {
                    throw new OpenAdtException(
                        "Cached HTTP SSO ticket was rejected (401). "
                            + "Reentrance tickets from browser SSO are not reusable across separate "
                            + "openadt fetch processes on this landscape. "
                            + "Keep one warm session: openadt proxy "
                            + system.getAlias()
                            + " --profile=sso (then fetch without another browser login), "
                            + "or run a single fetch per ticket."
                    );
                }
                CliLog.diagnostic("HTTP 401 — retrying once via browser callback");
                response = sendOnce(system, request);
            }
            return response;
        } catch (IOException e) {
            throw new OpenAdtException("Failed to execute HTTP ADT call: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenAdtException("Interrupted while executing HTTP ADT call", e);
        }
    }

    private ProxyResponse sendOnce(SystemProfile system, ProxyRequest request) throws IOException, InterruptedException {
        URI targetUri = buildTargetUri(system, request.uri());
        HttpRequest httpRequest = buildHttpRequest(system, request, targetUri);
        HttpResponse<byte[]> response = httpClientFor(system).send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            persistResolvedApiBase(system, targetUri);
            cookieProvider.recordResponseCookies(system, HttpSapCookieStore.fromSetCookieHeaders(response.headers()));
        }

        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((key, values) -> headers.put(key, String.join(", ", values)));

        return new ProxyResponse(
            DEFAULT_VERSION,
            response.statusCode(),
            reasonPhrase(response.statusCode()),
            headers,
            response.body()
        );
    }

    private void invalidateTicket(SystemProfile system) {
        cachedMysapsso2 = null;
        resolveUsedDiskCache.remove();
        cookieProvider.invalidateCachedTicket(system);
    }

    HttpRequest buildHttpRequest(SystemProfile system, ProxyRequest request, URI targetUri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri)
            .timeout(Duration.ofSeconds(60));

        request.headers().forEach(builder::header);
        builder.header(HEADER_COOKIE, buildCookieHeader(system));

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
        if (cachedMysapsso2 == null) {
            synchronized (this) {
                if (cachedMysapsso2 == null) {
                    AdtHttpCookieProvider.Mysapsso2Resolution resolution =
                        cookieProvider.resolveMysapsso2(config, system);
                    resolveUsedDiskCache.set(resolution.usedDiskCache());
                    cachedMysapsso2 = resolution.ticket();
                }
            }
        }
        if (cachedMysapsso2 == null || cachedMysapsso2.isBlank()) {
            throw new IllegalStateException(
                "HTTP ADT transport requires a MYSAPSSO2 ticket. "
                    + "Use browser SSO (destinations.<alias> discovery_url), OPENADT_MYSAPSSO2, "
                    + "secure_login.mysapsso2, or OPENADT_COOKIE_FILE."
            );
        }
        return HttpSapCookieStore.buildCookieHeader(
            cachedMysapsso2,
            system.getClient(),
            cookieProvider.lastSessionCookies()
        );
    }

    private URI discoverAdtApiBase(SystemProfile system) {
        if (system.getAdt() == null || system.getAdt().getDiscoveryUrl() == null || system.getAdt().getDiscoveryUrl().isBlank()) {
            throw new IllegalStateException(
                "HTTP ADT transport requires destinations.<alias>.adt.discovery_url to be configured with a logical frontend URL."
            );
        }

        URI configuredUri = normalizeBaseUri(system.getAdt().getDiscoveryUrl());

        Optional<HttpSsoTicketCache.CachedSession> cachedSession = ticketCache().readSession(system);
        if (cachedSession.isPresent() && cachedSession.get().hasApiBase()) {
            CliLog.httpSso("using cached ADT API base (skip well-known/virtualhost probes)");
            return normalizeBaseUri(cachedSession.get().apiBase());
        }

        if (Boolean.TRUE.equals(resolveUsedDiskCache.get())
            && configuredUri.getPath() != null
            && AdtHttpPaths.pathContainsAdtRoot(configuredUri.getPath())) {
            CliLog.httpSso("using discovery_url as ADT API base (cached ticket, skip well-known probes)");
            return configuredUri;
        }

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

        if (AdtHttpPaths.pathContainsAdtRoot(configuredUri.getPath())) {
            return configuredUri;
        }

        throw new IllegalStateException(
            "Unable to resolve an ADT API URL from discovery_url. Configure a logical frontend that exposes /.well-known/sap-adt-info or /sap/public/bc/icf/virtualhost."
        );
    }

    private URI normalizeBaseUri(String rawBase) {
        String value = rawBase;
        value = AdtHttpPaths.withHttpsSchemeIfMissing(value);
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
                .header(HEADER_COOKIE, buildCookieHeader(system))
                .GET()
                .build();
            HttpResponse<byte[]> response = httpClientFor(system).send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode serviceEndpoint = root.path("resource").path("service_endpoint");
            return serviceEndpoint.isTextual() ? serviceEndpoint.asText() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String readApiUrlFromVirtualHost(SystemProfile system, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header(HEADER_COOKIE, buildCookieHeader(system))
                .GET()
                .build();
            HttpResponse<byte[]> response = httpClientFor(system).send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode api = root.path("relatedUrls").path("api");
            return api.isTextual() ? api.asText() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpSsoTicketCache ticketCache() {
        return cookieProvider.ticketCacheForTransport();
    }

    private void persistResolvedApiBase(SystemProfile system, URI targetUri) {
        String path = targetUri.getPath() != null ? targetUri.getPath() : "";
        int adtIndex = path.indexOf(AdtHttpPaths.ADT_ICF_ROOT);
        if (adtIndex < 0) {
            return;
        }
        String basePath = path.substring(0, adtIndex + AdtHttpPaths.ADT_ICF_ROOT.length());
        String apiBase = targetUri.getScheme() + "://" + targetUri.getAuthority() + basePath;
        ticketCache().writeApiBase(system, apiBase);
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
