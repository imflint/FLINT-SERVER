package kr.flint.infra.tmdb.client;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class TmdbRequestRateLimiter {

    private final long intervalNanos;
    private long nextPermitNanos;

    public TmdbRequestRateLimiter(int requestsPerSecond) {
        if (requestsPerSecond < 1) {
            throw new IllegalArgumentException("TMDB requests per second must be greater than zero");
        }
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / requestsPerSecond;
    }

    public synchronized void acquire() {
        long now = System.nanoTime();
        long waitNanos = Math.max(0, nextPermitNanos - now);
        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while waiting for a TMDB request permit");
            }
        }
        nextPermitNanos = Math.max(System.nanoTime(), nextPermitNanos) + intervalNanos;
    }
}
