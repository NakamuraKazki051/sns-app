package com.snsapp.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** MySQL がデッドロックで取り消した処理を、やり直す(CurrentUser.retryOnDeadlock) */
class DeadlockRetryTest {

    @Test
    void aDeadlockedAttemptIsRetried() {
        AtomicInteger calls = new AtomicInteger();
        String result = CurrentUser.retryOnDeadlock(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new CannotAcquireLockException("Deadlock found when trying to get lock");
            }
            return "done";
        });
        assertEquals("done", result);
        assertEquals(2, calls.get());
    }

    @Test
    void itGivesUpAfterThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(CannotAcquireLockException.class, () -> CurrentUser.retryOnDeadlock(() -> {
            calls.incrementAndGet();
            throw new CannotAcquireLockException("Deadlock found when trying to get lock");
        }));
        assertEquals(CurrentUser.DEADLOCK_ATTEMPTS, calls.get());
    }
}
