package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.common.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest extends ApiTestSupport {

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private TokenService tokenService;

    @AfterEach
    void resetClock() {
        rateLimiter.setClock(Clock.systemUTC());
        tokenService.setClock(Clock.systemUTC());
    }

    @Test
    void registersLogsInReadsMeAndLogsOut() throws Exception {
        mockMvc.perform(json(post("/api/auth/register"), Map.of(
                        "email", "Alice@Example.com", "password", PASSWORD,
                        "username", "alice_01", "displayName", " アリス ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.username").value("alice_01"))
                .andExpect(jsonPath("$.displayName").value("アリス"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        MvcResult login = perform(json(post("/api/auth/login"), Map.of("email", "ALICE@example.com", "password", PASSWORD)));
        assertEquals(200, login.getResponse().getStatus());
        Cookie token = login.getResponse().getCookie(CurrentUser.COOKIE_NAME);
        // JWT は「ヘッダー.中身.署名」の3つ。JavaScript から読めず、30分で消える Cookie に入れる
        assertEquals(3, token.getValue().split("\\.").length);
        assertTrue(token.isHttpOnly());
        assertEquals("/", token.getPath());
        assertEquals(30 * 60, token.getMaxAge());
        assertEquals("Lax", token.getAttribute("SameSite"));
        assertFalse(token.getSecure());

        mockMvc.perform(get("/api/auth/me").cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_01"));
        // ログアウトはブラウザの Cookie を消す(期限0の空の Cookie を返す)
        Cookie cleared = perform(post("/api/auth/logout").cookie(token)).getResponse().getCookie(CurrentUser.COOKIE_NAME);
        assertEquals("", cleared.getValue());
        assertEquals(0, cleared.getMaxAge());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void eachLoginGetsANewToken() throws Exception {
        String username = newUsername();
        Cookie first = registerAndLogin(username);
        // 同じ秒の中だと同じ JWT になるので、時計を進める
        tokenService.setClock(Clock.offset(Clock.systemUTC(), Duration.ofSeconds(5)));
        assertNotEquals(first.getValue(), login(username).getValue());
    }

    @Test
    void brokenTamperedOrForeignTokensAreRejected() throws Exception {
        Cookie mine = registerAndLogin();
        String[] parts = mine.getValue().split("\\.");
        String otherUser = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"someone-else\",\"iat\":1,\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        String noneHeader = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        TokenService otherKey = new TokenService("another-secret-that-is-at-least-32-bytes", Duration.ofMinutes(30));
        List<String> rejected = List.of(
                "not-a-jwt",
                parts[0] + "." + otherUser + "." + parts[2],      // 中身を書き換えた
                noneHeader + "." + parts[1] + ".",                // 署名なしを名乗る
                otherKey.issue("someone-else"));                  // 別の鍵で署名した
        for (String value : rejected) {
            mockMvc.perform(get("/api/auth/me").cookie(new Cookie(CurrentUser.COOKIE_NAME, value)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theTokenExpiresThirtyMinutesAfterTheLastRequest() throws Exception {
        Instant start = Instant.now();
        tokenService.setClock(Clock.fixed(start, ZoneOffset.UTC));
        Cookie token = registerAndLogin();

        // 29分後に操作すると、新しい JWT が届き、そこから30分使える
        tokenService.setClock(Clock.fixed(start.plus(Duration.ofMinutes(29)), ZoneOffset.UTC));
        MvcResult used = perform(get("/api/timeline").cookie(token));
        assertEquals(200, used.getResponse().getStatus());
        Cookie renewed = used.getResponse().getCookie(CurrentUser.COOKIE_NAME);
        assertNotNull(renewed);

        tokenService.setClock(Clock.fixed(start.plus(Duration.ofMinutes(31)), ZoneOffset.UTC));
        mockMvc.perform(get("/api/timeline").cookie(token)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("ログインの有効期限が切れました。もう一度ログインしてください"));
        mockMvc.perform(get("/api/timeline").cookie(renewed)).andExpect(status().isOk());
    }

    @Test
    void aFreshTokenIsNotReissuedOnEveryRequest() throws Exception {
        Cookie token = registerAndLogin();
        MvcResult result = perform(get("/api/timeline").cookie(token));
        assertEquals(200, result.getResponse().getStatus());
        assertNull(result.getResponse().getCookie(CurrentUser.COOKIE_NAME));
    }

    @Test
    void secretsShorterThan32BytesAreRefused() {
        assertThrows(IllegalStateException.class, () -> new TokenService("short", Duration.ofMinutes(30)));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameMessage() throws Exception {
        String username = newUsername();
        registerAndLogin(username);
        MvcResult wrong = perform(json(post("/api/auth/login"),
                Map.of("email", username + "@example.com", "password", "wrong-password")));
        MvcResult unknown = perform(json(post("/api/auth/login"),
                Map.of("email", "nobody@example.com", "password", PASSWORD)));
        assertEquals(401, wrong.getResponse().getStatus());
        assertEquals(401, unknown.getResponse().getStatus());
        assertEquals(body(wrong).get("message"), body(unknown).get("message"));
    }

    @Test
    void tooManyLoginAttemptsForOneEmailAreRefusedForAWhile() throws Exception {
        String username = newUsername();
        registerAndLogin(username);
        String email = username + "@example.com";
        // 登録のあとのログインで1回使っている
        for (int i = 0; i < AuthController.LOGIN_ATTEMPTS - 1; i++) {
            mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        // 回数を超えたら、正しいパスワードでも受け付けない(大文字・空白違いのメールアドレスも同じ扱い)
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", " " + email.toUpperCase(), "password", PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("ログインの試行回数が多すぎます。5分ほど待ってからもう一度お試しください"));

        // 別のメールアドレスのログインは止めない
        registerAndLogin();

        rateLimiter.setClock(Clock.fixed(Instant.now().plus(AuthController.LOGIN_WINDOW).plusSeconds(1), ZoneOffset.UTC));
        login(username);
    }

    @Test
    void meAndLogoutWithoutASession() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void rejectsDuplicateOrInvalidEmailAndShortPassword() throws Exception {
        String username = newUsername();
        registerAndLogin(username);
        Map<String, Object> duplicate = registerParams(newUsername());
        duplicate.put("email", username.toUpperCase() + "@example.com");
        mockMvc.perform(json(post("/api/auth/register"), duplicate)).andExpect(status().isConflict());

        Map<String, Object> invalid = registerParams(newUsername());
        invalid.put("email", "not-an-email");
        mockMvc.perform(json(post("/api/auth/register"), invalid)).andExpect(status().isBadRequest());

        Map<String, Object> shortPassword = registerParams(newUsername());
        shortPassword.put("password", "1234567");
        mockMvc.perform(json(post("/api/auth/register"), shortPassword)).andExpect(status().isBadRequest());
    }

    @Test
    void usernameIsLettersDigitsUnderscoreUpTo15AndUniqueIgnoringCase() throws Exception {
        registerAndLogin("Taken_Name");
        mockMvc.perform(json(post("/api/auth/register"), registerParams("taken_name")))
                .andExpect(status().isConflict());

        for (String bad : List.of("", "a".repeat(16), "has space", "ハイフン", "dot.name", "dash-name")) {
            Map<String, Object> params = registerParams(newUsername());
            params.put("username", bad);
            mockMvc.perform(json(post("/api/auth/register"), params)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(json(post("/api/auth/register"), registerParams("a".repeat(15))))
                .andExpect(status().isCreated());
    }

    @Test
    void displayNameIsRequiredAndUpTo50Characters() throws Exception {
        Map<String, Object> blank = registerParams(newUsername());
        blank.put("displayName", "   ");
        mockMvc.perform(json(post("/api/auth/register"), blank)).andExpect(status().isBadRequest());

        Map<String, Object> tooLong = registerParams(newUsername());
        tooLong.put("displayName", "あ".repeat(51));
        mockMvc.perform(json(post("/api/auth/register"), tooLong)).andExpect(status().isBadRequest());

        Map<String, Object> max = registerParams(newUsername());
        max.put("displayName", "あ".repeat(50));
        mockMvc.perform(json(post("/api/auth/register"), max)).andExpect(status().isCreated());
    }

    @Test
    void passwordLongerThan72BytesIsABadRequest() throws Exception {
        // 全角文字は1文字3バイトなので、30文字でも72バイトを超える
        Map<String, Object> params = registerParams(newUsername());
        params.put("password", "あ".repeat(30));
        mockMvc.perform(json(post("/api/auth/register"), params)).andExpect(status().isBadRequest());
    }

    @Test
    void loginRejectsOversizedInputWithoutCountingIt() throws Exception {
        // メールアドレスは回数を数えるキーとしてメモリに残るので、巨大な値を受け付けてはいけない
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", "a".repeat(256), "password", PASSWORD)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", "a@example.com", "password", "a".repeat(73))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changingThePasswordKeepsBothDevicesLoggedInUntilTheTokensExpire() throws Exception {
        String username = newUsername();
        Cookie pc = registerAndLogin(username);
        Cookie phone = login(username);

        MvcResult changed = perform(json(put("/api/users/me").cookie(pc),
                Map.of("currentPassword", PASSWORD, "newPassword", "new-password-123")));
        assertEquals(200, changed.getResponse().getStatus());
        // 変えた本人には新しい JWT が届く。他の端末の JWT は取り消せない(サーバーは JWT を覚えていない)
        assertNotNull(changed.getResponse().getCookie(CurrentUser.COOKIE_NAME));
        mockMvc.perform(get("/api/timeline").cookie(pc)).andExpect(status().isOk());
        mockMvc.perform(get("/api/timeline").cookie(phone)).andExpect(status().isOk());

        mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("email", username + "@example.com", "password", "new-password-123")))
                .andExpect(status().isOk());
    }

    @Test
    void updateRequiresTheRightCurrentPasswordAFreeEmailAndALogin() throws Exception {
        String taken = newUsername();
        registerAndLogin(taken);
        Cookie session = registerAndLogin();
        mockMvc.perform(json(put("/api/users/me").cookie(session),
                        Map.of("currentPassword", "wrong-password", "email", "x@example.com")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(put("/api/users/me").cookie(session),
                        Map.of("currentPassword", PASSWORD, "email", taken + "@example.com")))
                .andExpect(status().isConflict());
        mockMvc.perform(json(put("/api/users/me"), Map.of("currentPassword", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
                .andExpect(status().isBadRequest());
    }
}
