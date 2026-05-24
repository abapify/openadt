package org.openadt.core;

import com.sap.adt.communication.message.AdtRequestFactory;
import com.sap.adt.communication.message.ByteArrayMessageBody;
import com.sap.adt.communication.message.IHeaders;
import com.sap.adt.communication.message.IMessageBody;
import com.sap.adt.communication.message.IRequest;
import com.sap.adt.communication.message.IRequestFactory;
import com.sap.adt.communication.message.IResponse;
import com.sap.adt.communication.session.AdtSystemSessionFactory;
import com.sap.adt.communication.session.IStatelessSystemSession;
import com.sap.adt.communication.session.ISystemSessionFactory;
import com.sap.adt.destinations.logon.AdtLogonServiceFactory;
import com.sap.adt.destinations.logon.IAdtLogonService;
import com.sap.adt.destinations.model.AdtDestinationDataFactory;
import com.sap.adt.destinations.model.IAuthenticationToken;
import com.sap.adt.destinations.model.IDestinationData;
import com.sap.adt.destinations.model.IDestinationDataWritable;
import com.sap.adt.destinations.model.ISystemConfiguration;
import com.sap.adt.destinations.model.ISystemConfigurationWritable;
import com.sap.adt.destinations.model.internal.SystemConfigurationWritable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executes ADT requests through the SAP ADT SDK (direct {@code com.sap.adt.*} API).
 */
public class AdtSdkTransportClient implements AdtTransportClient {
    private static final String HEADER_ACCEPT = "Accept";
    private static final Set<String> LOGGED_ON_DESTINATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Object> LOGON_LOCKS = new ConcurrentHashMap<>();

    private final OpenAdtConfig openAdtConfig;

    public AdtSdkTransportClient(OpenAdtConfig config) {
        this.openAdtConfig = config;
    }

    static void resetLogonCacheForTests() {
        LOGGED_ON_DESTINATIONS.clear();
        LOGON_LOCKS.clear();
    }

    /**
     * Warms JCo/SDK logon once before the proxy accepts IDE traffic.
     */
    public void warmUp(SystemProfile system) {
        execute(system, new ProxyRequest(
            "GET",
            "/sap/bc/adt/discovery",
            "HTTP/1.1",
            Map.of(HEADER_ACCEPT, "application/atomsvc+xml"),
            new byte[0]
        ));
    }

    @Override
    public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
        if (openAdtConfig == null) {
            throw new IllegalStateException("ADT SDK transport requires OpenAdt config (use AdtTransportFactory.create)");
        }
        try {
            SapSdkRuntime.prepare(openAdtConfig);
            SapDestinationResolver.ResolvedDestination resolved =
                SapDestinationResolver.resolve(system);
            log("destination id=" + resolved.destinationData().getId()
                + (resolved.fromEclipse() ? " (eclipse)" : " (config)"));
            return executeDestination(
                resolved.destinationData(),
                resolved.authenticationToken(),
                request
            );
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new OpenAdtException("Failed to execute ADT SDK call: " + error.getMessage(), error);
        }
    }

    private ProxyResponse executeDestination(
        IDestinationData destinationData,
        IAuthenticationToken authenticationToken,
        ProxyRequest request
    ) throws Exception {
        String destinationId = destinationData.getId();
        ensureLoggedOnOnce(destinationData, authenticationToken);
        IProgressMonitor monitor = new NullProgressMonitor();

        log("AdtSystemSessionFactory.createStatelessSession(" + destinationId + ")");
        ISystemSessionFactory sessionFactory = AdtSystemSessionFactory.createSystemSessionFactory();
        IStatelessSystemSession session = sessionFactory.createStatelessSession(destinationId);

        IRequest sdkRequest = createSdkRequest(request);
        log("session.sendRequest " + request.method() + " " + request.uri());
        IResponse sdkResponse = session.sendRequest(monitor, sdkRequest);
        return toProxyResponse(sdkResponse);
    }

    public static IDestinationData buildDestinationData(SystemProfile system) {
        String destinationId = system.getAlias() != null ? system.getAlias() : "openadt_dest";
        IDestinationDataWritable destinationDataWritable = AdtDestinationDataFactory.newDestinationData(destinationId);
        fillDestination(destinationDataWritable, destinationId, system);
        return destinationDataWritable.getReadOnlyClone();
    }

    private static void fillDestination(IDestinationDataWritable data, String destinationId, SystemProfile system) {
        data.setId(destinationId);
        if (system.getUser() != null) {
            data.setUser(system.getUser());
        }
        if (system.getClient() != null) {
            data.setClient(system.getClient());
        }
        if (system.getLanguage() != null) {
            data.setLanguage(system.getLanguage());
        }
        data.setSystemConfiguration(createSystemConfiguration(destinationId, system));
        data.setSystemConfigurationName(destinationId);
    }

    private static ISystemConfiguration createSystemConfiguration(String destinationId, SystemProfile system) {
        ISystemConfigurationWritable writable = new SystemConfigurationWritable(destinationId);
        applyBasicSystemFields(writable, system);
        SystemProfile.JcoConfig jco = system.getJco();
        if (jco != null) {
            applyJcoConnectionSettings(writable, system, jco);
            applySncSettings(writable, jco);
        }
        return writable;
    }

    private static void applyBasicSystemFields(ISystemConfigurationWritable writable, SystemProfile system) {
        if (system.getDescription() != null) {
            writable.setDescription(system.getDescription());
        }
        if (system.getSystemId() != null) {
            writable.setSystemId(system.getSystemId());
        }
        if (system.getClient() != null) {
            writable.setPreferredClient(system.getClient());
        }
        if (system.getLanguage() != null) {
            writable.setPreferredLanguage(system.getLanguage());
        }
        if (system.getUser() != null) {
            writable.setPreferredUser(system.getUser());
        }
    }

    private static void applyJcoConnectionSettings(
        ISystemConfigurationWritable writable,
        SystemProfile system,
        SystemProfile.JcoConfig jco
    ) {
        if (jco.getMshost() != null && !jco.getMshost().isBlank()) {
            writable.setMessageServer(jco.getMshost());
            if (jco.getMsserv() != null) {
                writable.setMessageServerService(jco.getMsserv());
            }
            if (jco.getGroup() != null) {
                writable.setGroup(jco.getGroup());
            }
            return;
        }
        String server = jco.getAshost();
        if ((server == null || server.isBlank()) && system.getAdt() != null) {
            server = system.getAdt().getAshost();
        }
        if (server != null && !server.isBlank()) {
            writable.setServer(server);
        }
        if (jco.getSysnr() != null) {
            writable.setSystemNumber(jco.getSysnr());
        }
    }

    private static void applySncSettings(ISystemConfigurationWritable writable, SystemProfile.JcoConfig jco) {
        boolean ssoEnabled = !"0".equals(jco.getSncSso());
        writable.setSSOEnabled(ssoEnabled);
        if (jco.getSncPartnername() != null && !jco.getSncPartnername().isBlank()) {
            writable.setPartnerName(jco.getSncPartnername());
            writable.setSNCType(resolveSncType(jco.getSncQop()));
        }
    }

    private static ISystemConfiguration.SNCType resolveSncType(String sncQop) {
        if (sncQop == null || sncQop.isBlank()) {
            return ISystemConfiguration.SNCType.SNC_DEFAULT;
        }
        try {
            return ISystemConfiguration.SNCType.getByCode(Integer.parseInt(sncQop.trim()));
        } catch (IllegalArgumentException error) {
            return ISystemConfiguration.SNCType.SNC_DEFAULT;
        }
    }

    public static IAuthenticationToken createAuthenticationTokenForSystem(SystemProfile system) {
        String destinationId = system.getAlias() != null ? system.getAlias() : "openadt_dest";
        IDestinationDataWritable writable = AdtDestinationDataFactory.newDestinationData(destinationId);
        fillDestination(writable, destinationId, system);
        return authenticationToken(writable, system);
    }

    private static IAuthenticationToken authenticationToken(
        IDestinationDataWritable destinationDataWritable,
        SystemProfile system
    ) {
        if (system.getJco() != null && "0".equals(system.getJco().getSncSso())) {
            return AdtDestinationDataFactory.createAuthenticationToken(destinationDataWritable);
        }
        return null;
    }

    private IRequest createSdkRequest(ProxyRequest request) {
        IHeaders headers = com.sap.adt.communication.message.HeadersFactory.newHeaders();
        if (request.headers() == null || request.getHeader(HEADER_ACCEPT) == null) {
            headers.addField(com.sap.adt.communication.message.HeadersFactory.newField(
                HEADER_ACCEPT,
                AdtAcceptHeaders.defaultAccept(request.uri())
            ));
        }
        if (request.headers() != null) {
            for (Map.Entry<String, String> entry : request.headers().entrySet()) {
                headers.addField(com.sap.adt.communication.message.HeadersFactory.newField(
                    entry.getKey(),
                    new String[]{entry.getValue()}
                ));
            }
        }

        IMessageBody body = null;
        if (request.body() != null && request.body().length > 0) {
            String contentType = request.getHeader("Content-Type");
            body = new ByteArrayMessageBody(contentType != null ? contentType : "application/octet-stream", request.body());
        }

        IRequestFactory requestFactory = AdtRequestFactory.createRequestFactory();
        IRequest.Method method = IRequest.Method.valueOf(request.method().toUpperCase(Locale.ROOT));
        URI uri = URI.create(request.uri());
        if (body != null) {
            return requestFactory.createInstance(method, uri, headers, body);
        }
        return requestFactory.createInstance(method, uri, headers, null);
    }

    private ProxyResponse toProxyResponse(IResponse sdkResponse) throws IOException {
        String version = sdkResponse.getVersion();
        int statusCode = sdkResponse.getStatus();
        String reasonPhrase = sdkResponse.getReasonPhrase();

        Map<String, String> fieldMap = new LinkedHashMap<>();
        if (sdkResponse.getHeaders() != null) {
            fieldMap.putAll(sdkResponse.getHeaders().getFieldMap());
        }

        byte[] body = new byte[0];
        if (sdkResponse.getBody() != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sdkResponse.getBody().writeTo(out);
            body = out.toByteArray();
        }

        return new ProxyResponse(
            version != null && !version.isBlank() ? version : "HTTP/1.1",
            statusCode,
            reasonPhrase,
            fieldMap,
            body
        );
    }

    private void ensureLoggedOnOnce(IDestinationData destinationData, IAuthenticationToken authenticationToken)
        throws Exception {
        String destinationId = destinationData.getId();
        if (LOGGED_ON_DESTINATIONS.contains(destinationId)) {
            return;
        }
        Object lock = LOGON_LOCKS.computeIfAbsent(destinationId, id -> new Object());
        synchronized (lock) {
            if (LOGGED_ON_DESTINATIONS.contains(destinationId)) {
                return;
            }
            log("AdtLogonServiceFactory.createLogonService().ensureLoggedOn (first time for " + destinationId + ")");
            IAdtLogonService logonService = AdtLogonServiceFactory.createLogonService();
            IStatus logonStatus = logonService.ensureLoggedOn(
                destinationData,
                authenticationToken,
                new NullProgressMonitor()
            );
            ensureOkStatus(logonStatus, "Failed to log on through ADT SDK");
            LOGGED_ON_DESTINATIONS.add(destinationId);
        }
    }

    private void ensureOkStatus(IStatus status, String context) {
        if (status == null) {
            return;
        }
        if (!status.isOK()) {
            throw new IllegalStateException(context + ": " + status.getMessage());
        }
    }

    private static void log(String message) {
        if (!Boolean.parseBoolean(System.getenv().getOrDefault("OPENADT_VERBOSE", "false"))) {
            return;
        }
        CliLog.error("[openadt sdk] " + message);
        CliLog.stderr().flush();
    }
}
