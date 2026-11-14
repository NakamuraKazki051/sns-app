package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** アクセストークン(15分)とリフレッシュトークン(7日)。取り替え・使い回しの検知・無効にする操作 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshTokenTest extends ApiTestSupport {

    @Autowired
    private TokenService tokenService;

    private Instant start;

    @AfterEach
    void resetClock() {
        tokenService.setClock(Clock.systemUTC());
    }

    /** ログインして、アクセストークンとリフレッシュトークンの Cookie を返す */
    private record Tokens(Cookie access, Cookie refresh) {
        static Tokens of(MvcResult result) {
            return new Tokens(result.getResponse().getCookie(CurrentUser.COOKIE_NAME),
                    result.getResponse().getCookie(CurrentUser.REFRESH_COOKIE_NAME));
        }
    }

    private Tokens signIn(String username) throws Exception {
        MvcResult result = perform(json(post("/api/auth/login"),
                Map.of("email", username + "@example.com", "password", PASSWORD)));
        assertEquals(200, result.getResponse().getStatus());
        return Tokens.of(result);
    }

    private String registerUser() throws Exception {
        String username = newUsername();
        mockMvc.perform(json(post("/api/auth/register"), registerParams(username))).andExpect(status().isCreated());
        return username;
    }

    private void at(Duration elapsed) {
        tokenService.setClock(Clock.fixed(start.plus(elapsed), ZoneOffset.UTC));
    }

    private MvcResult refresh(Cookie refresh) throws Exception {
        return perform(post("/api/auth/refresh").cookie(refresh));
    }

    private void startClock() {
        // DB は時刻をマイクロ秒に丸めるので、秒にそろえておく(境目の比べ方が丸め方に左右されないように)
        start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        at(Duration.ZERO);
    }

    @Test
    void anExpiredAccessTokenIsReissuedWithANewRefreshToken() throws Exception {
        startClock();
        Tokens tokens = signIn(registerUser());

        at(Duration.ofMinutes(20));
        mockMvc.perform(get("/api/timeline").cookie(tokens.access())).andExpect(status().isUnauthorized());
        MvcResult refreshed = refresh(tokens.refresh());
        assertEquals(204, refreshed.getResponse().getStatus());
        Tokens next = Tokens.of(refreshed);
        assertNotNull(next.access());
        assertNotNull(next.refresh());
        assertNotEquals(tokens.refresh().getValue(), next.refresh().getValue());
        assertEquals(7 * 24 * 60 * 60, next.refresh().getMaxAge());
        mockMvc.perform(get("/api/timeline").cookie(next.access())).andExpect(status().isOk());
    }

    @Test
    void usingTheRefreshTokenKeepsTheLoginAliveForSevenDaysFromTheLastUse() throws Exception {
        startClock();
        Tokens tokens = signIn(registerUser());

        // 6日後に使えば、そこから7日使える
        at(Duration.ofDays(6));
        Tokens renewed = Tokens.of(refresh(tokens.refresh()));
        at(Duration.ofDays(12));
        assertEquals(204, refresh(renewed.refresh()).getResponse().getStatus());
    }

    @Test
    void aRefreshTokenUnusedForSevenDaysExpires() throws Exception {
        startClock();
        Tokens tokens = signIn(registerUser());

        at(Duration.ofDays(7).plusSeconds(1));
        MvcResult result = refresh(tokens.refresh());
        assertEquals(401, result.getResponse().getStatus());
        // 使えないリフレッシュトークンは、ブラウザからも消す
        assertEquals(0, result.getResponse().getCookie(CurrentUser.REFRESH_COOKIE_NAME).getMaxAge());
    }

    @Test
    void reusingAnOldRefreshTokenEndsTheWholeLogin() throws Exception {
        startClock();
        String username = registerUser();
        Tokens stolen = signIn(username);
        Tokens otherDevice = signIn(username);

        // 本人が取り替えたあとで、盗んだ人が古いものを使った
        at(Duration.ofMinutes(20));
        Tokens owner = Tokens.of(refresh(stolen.refresh()));
        at(Duration.ofMinutes(20).plus(RefreshTokens.REUSE_GRACE).plusSeconds(1));
        assertEquals(401, refresh(stolen.refresh()).getResponse().getStatus());

        // どちらが本物か分からないので、そのログインの新しいトークンもすべて使えない
        assertEquals(401, refresh(owner.refresh()).getResponse().getStatus());
        mockMvc.perform(get("/api/timeline").cookie(owner.access())).andExpect(status().isUnauthorized());
        // ほかの端末のログインはそのまま
        assertEquals(204, refresh(otherDevice.refresh()).getResponse().getStatus());
    }

    @Test
    void aSecondTabRefreshingAtTheSameTimeIsNotTreatedAsTheft() throws Exception {
        startClock();
        Tokens tokens = signIn(registerUser());

        at(Duration.ofMinutes(20));
        Tokens first = Tokens.of(refresh(tokens.refresh()));
        // 遅れて届いた方は、何も変えずに 204(Cookie は先に届いた新しいものを使う)
        at(Duration.ofMinutes(20).plusSeconds(5));
        MvcResult late = refresh(tokens.refresh());
        assertEquals(204, late.getResponse().getStatus());
        assertNull(late.getResponse().getCookie(CurrentUser.COOKIE_NAME));
        assertNull(late.getResponse().getCookie(CurrentUser.REFRESH_COOKIE_NAME));

        mockMvc.perform(get("/api/timeline").cookie(first.access())).andExpect(status().isOk());
        assertEquals(204, refresh(first.refresh()).getResponse().getStatus());
    }

    @Test
    void logoutEndsTheRefreshTokenEvenAfterTheAccessTokenExpired() throws Exception {
        startClock();
        Tokens tokens = signIn(registerUser());

        at(Duration.ofMinutes(20));
        mockMvc.perform(post("/api/auth/logout").cookie(tokens.access(), tokens.refresh()))
                .andExpect(status().isNoContent());
        assertEquals(401, refresh(tokens.refresh()).getResponse().getStatus());
    }

    @Test
    void logoutEndsBothTokensOfThisDeviceOnly() throws Exception {
        startClock();
        String username = registerUser();
        Tokens pc = signIn(username);
        Tokens phone = signIn(username);

        mockMvc.perform(post("/api/auth/logout").cookie(pc.access(), pc.refresh())).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/timeline").cookie(pc.access())).andExpect(status().isUnauthorized());
        assertEquals(401, refresh(pc.refresh()).getResponse().getStatus());
        mockMvc.perform(get("/api/timeline").cookie(phone.access())).andExpect(status().isOk());
        assertEquals(204, refresh(phone.refresh()).getResponse().getStatus());
    }

    @Test
    void changingThePasswordEndsOtherDevicesRefreshTokens() throws Exception {
        startClock();
        String username = registerUser();
        Tokens pc = signIn(username);
        Tokens phone = signIn(username);

        MvcResult changed = perform(json(put("/api/users/me").cookie(pc.access()),
                Map.of("currentPassword", PASSWORD, "newPassword", "new-password-123")));
        assertEquals(200, changed.getResponse().getStatus());
        assertEquals(401, refresh(phone.refresh()).getResponse().getStatus());
        assertEquals(401, refresh(pc.refresh()).getResponse().getStatus());
        // 変えた本人には、新しいリフレッシュトークンも届く
        assertEquals(204, refresh(Tokens.of(changed).refresh()).getResponse().getStatus());
    }

    @Test
    void deletingTheAccountEndsTheRefreshTokens() throws Exception {
        startClock();
        String username = registerUser();
        Tokens pc = signIn(username);
        Tokens phone = signIn(username);

        mockMvc.perform(json(delete("/api/users/me").cookie(pc.access()), Map.of("password", PASSWORD)))
                .andExpect(status().isNoContent());
        assertEquals(401, refresh(phone.refresh()).getResponse().getStatus());
    }

    @Test
    void refreshWithoutOrWithAnUnknownTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(CurrentUser.REFRESH_COOKIE_NAME, "unknown")))
                .andExpect(status().isUnauthorized());
    }
}
