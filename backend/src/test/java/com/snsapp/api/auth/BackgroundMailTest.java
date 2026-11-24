package com.snsapp.api.auth;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 登録の有無を明かさないメールを、応答のあとに送る */
class BackgroundMailTest {

    @Test
    void sendsOnAnotherThreadWithoutMakingTheCallerWait() throws Exception {
        BackgroundMail mail = new BackgroundMail(true);
        try {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Thread> sender = new AtomicReference<>();
            // 送るのに時間がかかっても(ここでは release まで止まる)、呼んだ側はすぐ戻る
            mail.run(() -> {
                sender.set(Thread.currentThread());
                awaitQuietly(release);
                done.countDown();
            });
            release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread(), sender.get());
        } finally {
            mail.shutdown();
        }
    }

    @Test
    void aFailureDoesNotStopLaterMails() throws Exception {
        BackgroundMail mail = new BackgroundMail(true);
        try {
            CountDownLatch done = new CountDownLatch(1);
            mail.run(() -> {
                throw new IllegalStateException("送れません");
            });
            mail.run(done::countDown);
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            mail.shutdown();
        }
    }

    @Test
    void inTestsItSendsRightAwayAndSwallowsFailures() {
        BackgroundMail mail = new BackgroundMail(false);
        boolean[] sent = new boolean[1];
        mail.run(() -> sent[0] = true);
        assertTrue(sent[0]);
        mail.run(() -> {
            throw new IllegalStateException("送れません");
        });
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
