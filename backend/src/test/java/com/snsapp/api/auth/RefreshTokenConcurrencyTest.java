package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 同じリフレッシュトークンへの取り替えとログアウトが同時に来たとき。
 * 本当に並べて動かすので、テスト全体をトランザクションで包まない(包むと同時に動かせない)
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenConcurrencyTest extends ApiTestSupport {

    private static final int ROUNDS = 10;

    private Cookie[] signIn(String username) throws Exception {
        MvcResult result = perform(json(post("/api/auth/login"),
                Map.of("email", username + "@example.com", "password", PASSWORD)));
        assertEquals(200, result.getResponse().getStatus());
        return new Cookie[]{result.getResponse().getCookie(CurrentUser.COOKIE_NAME),
                result.getResponse().getCookie(CurrentUser.REFRESH_COOKIE_NAME)};
    }

    /** 2つの操作を、できるだけ同時に始める */
    private MockHttpServletResponse[] together(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(1);
            Future<MvcResult> a = pool.submit(() -> {
                ready.await();
                return first.call();
            });
            Future<MvcResult> b = pool.submit(() -> {
                ready.await();
                return second.call();
            });
            ready.countDown();
            return new MockHttpServletResponse[]{a.get().getResponse(), b.get().getResponse()};
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void twoRefreshesOfTheSameTokenRotateItOnlyOnce() throws Exception {
        String username = newUsername();
        mockMvc.perform(json(post("/api/auth/register"), registerParams(username)));
        for (int i = 0; i < ROUNDS; i++) {
            Cookie refresh = signIn(username)[1];
            MockHttpServletResponse[] results = together(
                    () -> perform(post("/api/auth/refresh").cookie(refresh)),
                    () -> perform(post("/api/auth/refresh").cookie(refresh)));
            // どちらも 204。新しいトークンを受け取るのは片方だけ(2本に分かれない)
            int rotated = 0;
            for (MockHttpServletResponse response : results) {
                assertEquals(204, response.getStatus());
                if (response.getCookie(CurrentUser.REFRESH_COOKIE_NAME) != null) {
                    rotated++;
                }
            }
            assertEquals(1, rotated);
        }
    }

    @Test
    void aRefreshRacingALogoutNeverKeepsTheLoginAlive() throws Exception {
        String username = newUsername();
        mockMvc.perform(json(post("/api/auth/register"), registerParams(username)));
        for (int i = 0; i < ROUNDS; i++) {
            Cookie[] tokens = signIn(username);
            MockHttpServletResponse[] results = together(
                    () -> perform(post("/api/auth/refresh").cookie(tokens[1])),
                    () -> perform(post("/api/auth/logout").cookie(tokens)));
            MockHttpServletResponse refresh = results[0];
            assertEquals(204, results[1].getStatus());
            if (refresh.getStatus() == 204) {
                // ログアウトより先に取り替えた。新しいトークンも、ログアウトでそのログインごと消えている
                Cookie renewed = refresh.getCookie(CurrentUser.REFRESH_COOKIE_NAME);
                assertNotNull(renewed, "取り替えていないのに 204 を返した");
                assertEquals(401, perform(post("/api/auth/refresh").cookie(renewed)).getResponse().getStatus());
            } else {
                // ログアウトの後に届いた。使えないので 401(「同時に作り直した」と取り違えて 204 にしない)
                assertEquals(401, refresh.getStatus());
            }
        }
    }
}
