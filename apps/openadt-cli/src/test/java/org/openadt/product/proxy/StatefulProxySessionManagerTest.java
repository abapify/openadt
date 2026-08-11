package org.openadt.product.proxy;

import org.junit.jupiter.api.Test;
import org.openadt.config.SystemProfile;
import org.openadt.sap.adt.sdk.AdtTransportClient;
import org.openadt.sap.adt.sdk.ProxyRequest;
import org.openadt.sap.adt.sdk.ProxyResponse;
import org.openadt.sap.adt.sdk.StatefulAdtTransportSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatefulProxySessionManagerTest {
    private final SystemProfile system = new SystemProfile();

    @Test
    void pinsLockPutAndUnlockToOneSdkSessionThenClosesIt() {
        RecordingTransport transport = new RecordingTransport();
        StatefulProxySessionManager manager = new StatefulProxySessionManager(transport);

        StatefulProxySessionManager.Result lock = manager.execute(
            system, stateful("POST", "/sap/bc/adt/oo/classes/ZCL_TEST?_action=LOCK"), null);
        manager.execute(system, stateful("PUT", "/sap/bc/adt/oo/classes/ZCL_TEST/source/main?lockHandle=abc"),
            lock.newSessionId());
        manager.execute(system, stateful("POST", "/sap/bc/adt/oo/classes/ZCL_TEST?_action=UNLOCK&lockHandle=abc"),
            lock.newSessionId());

        assertEquals(1, transport.openedSessions);
        assertEquals(3, transport.session.calls);
        assertEquals(1, transport.session.closed);
        assertEquals(0, manager.activeSessionCount());
    }

    @Test
    void keepsRequestsWithoutStatefulHeaderOnTheStatelessTransport() {
        RecordingTransport transport = new RecordingTransport();
        StatefulProxySessionManager manager = new StatefulProxySessionManager(transport);

        StatefulProxySessionManager.Result result = manager.execute(system,
            new ProxyRequest("GET", "/sap/bc/adt/discovery", "HTTP/1.1", Map.of(), new byte[0]), null);

        assertNull(result.newSessionId());
        assertEquals(1, transport.statelessCalls);
        assertEquals(0, transport.openedSessions);
    }

    @Test
    void ignoresStatefulCookieForNonStatefulRequests() {
        RecordingTransport transport = new RecordingTransport();
        StatefulProxySessionManager manager = new StatefulProxySessionManager(transport);

        StatefulProxySessionManager.Result lock = manager.execute(
            system, stateful("POST", "/sap/bc/adt/oo/classes/ZCL_TEST?_action=LOCK"), null);

        StatefulProxySessionManager.Result discovery = manager.execute(system,
            new ProxyRequest("GET", "/sap/bc/adt/discovery", "HTTP/1.1", Map.of(), new byte[0]),
            lock.newSessionId());

        assertNull(discovery.newSessionId());
        assertEquals(1, transport.statelessCalls);
        assertEquals(1, transport.openedSessions);
        assertEquals(1, manager.activeSessionCount());
    }

    @Test
    void closesStatefulSessionOnExplicitStatelessHeader() {
        RecordingTransport transport = new RecordingTransport();
        StatefulProxySessionManager manager = new StatefulProxySessionManager(transport);

        StatefulProxySessionManager.Result lock = manager.execute(
            system, stateful("POST", "/sap/bc/adt/oo/classes/ZCL_TEST?_action=LOCK"), null);

        StatefulProxySessionManager.Result clear = manager.execute(system,
            new ProxyRequest("POST", "/sap/bc/adt/oo/classes/ZCL_TEST", "HTTP/1.1",
                Map.of("X-sap-adt-sessiontype", "stateless"), new byte[0]),
            lock.newSessionId());

        assertNull(clear.newSessionId());
        assertEquals(1, transport.statelessCalls);
        assertEquals(1, transport.session.closed);
        assertEquals(0, manager.activeSessionCount());
    }

    @Test
    void replacesAnUnknownCookieWithANewStatefulSession() {
        RecordingTransport transport = new RecordingTransport();
        StatefulProxySessionManager manager = new StatefulProxySessionManager(transport);

        StatefulProxySessionManager.Result result = manager.execute(
            system,
            stateful("POST", "/sap/bc/adt/oo/classes/ZCL_TEST?_action=LOCK"),
            "expired-session"
        );

        assertEquals(1, transport.openedSessions);
        assertEquals(1, transport.session.calls);
        assertEquals(1, manager.activeSessionCount());
        assertEquals(false, result.newSessionId() == null || result.newSessionId().isBlank());
    }

    private static ProxyRequest stateful(String method, String uri) {
        return new ProxyRequest(method, uri, "HTTP/1.1",
            Map.of("X-sap-adt-sessiontype", "stateful"), new byte[0]);
    }

    private static final class RecordingTransport implements AdtTransportClient {
        private int statelessCalls;
        private int openedSessions;
        private final RecordingStatefulSession session = new RecordingStatefulSession();

        @Override
        public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
            statelessCalls++;
            return ok();
        }

        @Override
        public boolean supportsStatefulSessions() {
            return true;
        }

        @Override
        public StatefulAdtTransportSession openStatefulSession(SystemProfile system) {
            openedSessions++;
            return session;
        }
    }

    private static final class RecordingStatefulSession implements StatefulAdtTransportSession {
        private int calls;
        private int closed;

        @Override
        public ProxyResponse execute(ProxyRequest request) {
            calls++;
            return ok();
        }

        @Override
        public void close() {
            closed++;
        }
    }

    private static ProxyResponse ok() {
        return new ProxyResponse("HTTP/1.1", 200, "OK", Map.of(), new byte[0]);
    }
}
