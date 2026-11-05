package com.snsapp.api.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    private static final Instant START = Instant.parse("2026-11-05T00:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private static Clock at(Instant now) {
        return Clock.fixed(now, ZoneOffset.UTC);
    }

    @Test
    void limitsAttemptsWithinTheWindowAndAllowsThemAgainAfterIt() {
        RateLimiter limiter = new RateLimiter();
        limiter.setClock(at(START));
        assertTrue(limiter.tryAcquire("a", 1, WINDOW));
        assertFalse(limiter.tryAcquire("a", 1, WINDOW));

        limiter.setClock(at(START.plus(WINDOW)));
        assertTrue(limiter.tryAcquire("a", 1, WINDOW));
    }

    @Test
    void forgetsKeysThatAreNoLongerUsed() {
        RateLimiter limiter = new RateLimiter();
        limiter.setClock(at(START));
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("login:" + i, 10, WINDOW);
        }
        assertEquals(100, limiter.trackedKeys());

        // 二度と来ないキーも、時間がたてば誰かの操作のついでに消える(メモリに残り続けない)
        limiter.setClock(at(START.plus(Duration.ofHours(3))));
        limiter.tryAcquire("other", 10, WINDOW);
        assertEquals(1, limiter.trackedKeys());
    }
}
