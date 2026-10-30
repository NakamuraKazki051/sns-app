package com.snsapp.api.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「一定時間に何回まで」を数える。ログインの総当たり対策と連投対策に使う。
 * サーバーは1台・1プロセスなので、回数はメモリに置けば足りる(再起動で数え直しになる)
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    /** 回数内なら1回分として数えて true。超えていたら数えずに false */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        boolean[] allowed = new boolean[1];
        attempts.compute(key, (k, times) -> {
            Deque<Instant> recent = times != null ? times : new ArrayDeque<>();
            while (!recent.isEmpty() && !recent.peekFirst().isAfter(now.minus(window))) {
                recent.pollFirst();
            }
            allowed[0] = recent.size() < limit;
            if (allowed[0]) {
                recent.addLast(now);
            }
            return recent.isEmpty() ? null : recent;
        });
        return allowed[0];
    }

    /** テストで時間を進めるため */
    public void setClock(Clock clock) {
        this.clock = clock;
    }
}
