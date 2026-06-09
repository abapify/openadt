package org.openadt.sap.adt.services.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentThrottleTest {

    @BeforeEach
    void reset() {
        AgentThrottle.resetForTests();
    }

    @Test
    void blankDestinationAlwaysPermits() {
        assertTrue(AgentThrottle.acquire(null));
        assertTrue(AgentThrottle.acquire(""));
        assertTrue(AgentThrottle.acquire("   "));
    }

    @Test
    void bucketStartsFullThenDrains() {
        // Capacity is 4.
        assertTrue(AgentThrottle.acquire("DEV"));
        assertTrue(AgentThrottle.acquire("DEV"));
        assertTrue(AgentThrottle.acquire("DEV"));
        assertTrue(AgentThrottle.acquire("DEV"));
        // 5th immediate call must be throttled.
        assertFalse(AgentThrottle.acquire("DEV"));
    }

    @Test
    void bucketsAreScopedPerDestination() {
        // Drain DEV.
        for (int i = 0; i < 4; i++) {
            assertTrue(AgentThrottle.acquire("DEV"));
        }
        assertFalse(AgentThrottle.acquire("DEV"));
        // QAS has its own bucket — must still allow.
        assertTrue(AgentThrottle.acquire("QAS"));
    }

    @Test
    void refillsAfterDelay() throws InterruptedException {
        // Drain DEV.
        for (int i = 0; i < 4; i++) {
            AgentThrottle.acquire("DEV");
        }
        assertFalse(AgentThrottle.acquire("DEV"));
        // Refill interval is 250ms; sleep slightly more.
        Thread.sleep(300);
        assertTrue(AgentThrottle.acquire("DEV"));
    }
}
