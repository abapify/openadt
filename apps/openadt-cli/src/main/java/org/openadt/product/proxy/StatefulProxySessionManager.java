package org.openadt.product.proxy;

import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;
import org.openadt.sap.adt.sdk.StatefulAdtTransportSession;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps an opaque local cookie to an SDK stateful ADT session.
 *
 * <p>The cookie stays at the proxy host; it is never included in the SAP request.
 * Only requests carrying {@code X-sap-adt-sessiontype: stateful} use a stateful session.
 * Non-stateful requests ignore any session cookie and use the stateless transport path.
 */
final class StatefulProxySessionManager implements AutoCloseable {
    static final String SESSION_COOKIE = "OPENADT_STATEFUL_SESSION";
    private static final String SESSION_TYPE_HEADER = "X-sap-adt-sessiontype";

    private final AdtTransportClient transportClient;
    private final Map<String, StatefulAdtTransportSession> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    StatefulProxySessionManager(AdtTransportClient transportClient) {
        this.transportClient = transportClient;
    }

    Result execute(SystemProfile system, ProxyRequest request, String suppliedSessionId) {
        StatefulAdtTransportSession session = sessionFor(system, request, suppliedSessionId);
        String newSessionId = session == null ? null : registeredSessionId(suppliedSessionId, session);
        boolean executionFailed = true;
        try {
            ProxyResponse response = session == null
                ? transportClient.execute(system, request)
                : session.execute(request);
            executionFailed = false;
            return new Result(response, newSessionId);
        } finally {
            closeAfter(request, suppliedSessionId, newSessionId, session, executionFailed);
        }
    }

    int activeSessionCount() {
        return sessions.size();
    }

    @Override
    public void close() {
        sessions.values().forEach(StatefulAdtTransportSession::close);
        sessions.clear();
    }

    private StatefulAdtTransportSession sessionFor(
        SystemProfile system,
        ProxyRequest request,
        String suppliedSessionId
    ) {
        if (!isStateful(request) || !transportClient.supportsStatefulSessions()) {
            return null;
        }
        StatefulAdtTransportSession existing = suppliedSessionId == null ? null : sessions.get(suppliedSessionId);
        if (existing != null) {
            return existing;
        }
        // Do not open a brand-new stateful session just to close it (e.g. UNLOCK with no cookie).
        if (shouldClose(request)) {
            return null;
        }
        return transportClient.openStatefulSession(system);
    }

    private String registeredSessionId(String suppliedSessionId, StatefulAdtTransportSession session) {
        if (suppliedSessionId != null && sessions.get(suppliedSessionId) == session) {
            return null;
        }
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String sessionId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(sessionId, session);
        return sessionId;
    }

    private void closeAfter(
        ProxyRequest request,
        String suppliedSessionId,
        String newSessionId,
        StatefulAdtTransportSession session,
        boolean executionFailed
    ) {
        boolean close = shouldClose(request) || (executionFailed && newSessionId != null);
        if (!close) {
            return;
        }
        StatefulAdtTransportSession sessionToClose = session;
        String sessionId = newSessionId != null ? newSessionId : suppliedSessionId;
        if (sessionToClose == null && suppliedSessionId != null) {
            sessionToClose = sessions.get(suppliedSessionId);
            sessionId = suppliedSessionId;
        }
        if (sessionToClose == null) {
            return;
        }
        sessions.remove(sessionId, sessionToClose);
        sessionToClose.close();
    }

    private static boolean isStateful(ProxyRequest request) {
        return "stateful".equalsIgnoreCase(request.getHeader(SESSION_TYPE_HEADER));
    }

    private static boolean shouldClose(ProxyRequest request) {
        if ("stateless".equalsIgnoreCase(request.getHeader(SESSION_TYPE_HEADER))) {
            return true;
        }
        String uri = request.uri();
        int queryStart = uri.indexOf('?');
        if (queryStart < 0) {
            return false;
        }
        for (String parameter : uri.substring(queryStart + 1).split("&")) {
            String[] keyValue = parameter.split("=", 2);
            if (keyValue.length == 2
                && "_action".equalsIgnoreCase(keyValue[0])
                && "UNLOCK".equalsIgnoreCase(keyValue[1])) {
                return true;
            }
        }
        return false;
    }

    record Result(ProxyResponse response, String newSessionId) { }
}
