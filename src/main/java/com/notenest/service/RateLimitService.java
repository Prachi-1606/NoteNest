package com.notenest.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory hourly rate limiter for Gemini API calls. Keeps a counter
 * that resets after a rolling 60-minute window starting from the first call
 * of the window. Backed by AtomicInteger; mutations are guarded by
 * {@code synchronized} since the reset/increment must be atomic together.
 *
 * Not a distributed limiter — every JVM instance gets its own counter.
 * Fine for a single-instance Render free-tier deploy.
 */
@Service
public class RateLimitService {

    public static final int MAX_CALLS_PER_HOUR = 12;

    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile Instant windowStart = Instant.now();

    /** Returns true if quota remains and reserves one slot; false otherwise. */
    public synchronized boolean tryAcquire() {
        rolloverIfExpired();
        if (counter.get() >= MAX_CALLS_PER_HOUR) {
            return false;
        }
        counter.incrementAndGet();
        return true;
    }

    public synchronized int remaining() {
        rolloverIfExpired();
        return Math.max(0, MAX_CALLS_PER_HOUR - counter.get());
    }

    private void rolloverIfExpired() {
        Instant now = Instant.now();
        if (Duration.between(windowStart, now).toMinutes() >= 60) {
            counter.set(0);
            windowStart = now;
        }
    }
}
