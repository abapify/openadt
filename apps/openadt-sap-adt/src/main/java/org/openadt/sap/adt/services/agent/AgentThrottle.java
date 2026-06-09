package org.openadt.sap.adt.services.agent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-destination token-bucket throttle for agent-foundation services.
 *
 * <p>The LSP server-side extensions throttle per-object calls
 * ({@code AdtLsThrottler}); we mirror that protection client-side to avoid
 * overwhelming the destination. Default rate: 4 permits per second per
 * destination, burst 4. The bucket refills continuously.</p>
 *
 * <p>Callers wrap their SDK invocation in {@link #acquire(String)}; if the
 * bucket is empty, the call returns false and the caller maps that to an
 * {@link AgentErrorCode#THROTTLED} envelope.</p>
 */
public final class AgentThrottle {

    private static final long REFILL_NANOS = 250_000_000L; // 4 permits / second
    private static final long CAPACITY = 4L;

    private static final ConcurrentHashMap<String, Bucket> BUCKETS = new ConcurrentHashMap<>();

    private AgentThrottle() {
    }

    /** Reset all buckets. Test-only. */
    public static void resetForTests() {
        BUCKETS.clear();
    }

    /**
     * Try to acquire one permit for {@code destinationId}. Returns
     * {@code true} if the call may proceed, {@code false} if throttled.
     */
    public static boolean acquire(String destinationId) {
        if (destinationId == null || destinationId.isBlank()) {
            return true; // nothing to throttle against
        }
        Bucket bucket = BUCKETS.computeIfAbsent(destinationId, key -> new Bucket());
        return bucket.tryConsume();
    }

    private static final class Bucket {
        private final AtomicLong permits = new AtomicLong(CAPACITY);
        private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());

        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            long last = lastRefillNanos.get();
            long elapsed = now - last;
            if (elapsed >= REFILL_NANOS) {
                long refill = Math.min(CAPACITY, elapsed / REFILL_NANOS);
                permits.set(Math.min(CAPACITY, permits.get() + refill));
                lastRefillNanos.set(last + refill * REFILL_NANOS);
            }
            long current = permits.get();
            while (current > 0) {
                if (permits.compareAndSet(current, current - 1)) {
                    return true;
                }
                current = permits.get();
            }
            return false;
        }
    }
}
