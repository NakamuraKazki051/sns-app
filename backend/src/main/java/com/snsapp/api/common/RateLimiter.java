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
 * サーバーは1台・1プロセスなので、回数はメモリに置けば足りる(再起動で数え直しになる)。
 * 二度と来ないキー(存在しないメールアドレスなど)が溜まり続けないよう、古いものは定期的に捨てる
 */
@Component
public class RateLimiter {

    // 使っている時間枠(最長1時間)より長くしておく。これより前の記録はどの制限にも効かない
    private static final Duration RETENTION = Duration.ofHours(2);
    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(10);

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();
    private volatile Instant nextSweep = Instant.MIN;

    /** 回数内なら1回分として数えて true。超えていたら数えずに false */
    public boolean tryAcquire(String key, int limit, Duration window) {
        Instant now = clock.instant();
        sweepIfDue(now);
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

    private void sweepIfDue(Instant now) {
        if (now.isBefore(nextSweep)) {
            return;
        }
        nextSweep = now.plus(SWEEP_INTERVAL);
        Instant oldest = now.minus(RETENTION);
        for (String key : attempts.keySet()) {
            attempts.computeIfPresent(key, (k, times) -> times.peekLast().isBefore(oldest) ? null : times);
        }
    }

    /** 覚えているキーの数(テスト用) */
    int trackedKeys() {
        return attempts.size();
    }

    /** テストで時間を進めるため */
    public void setClock(Clock clock) {
        this.clock = clock;
    }
}
