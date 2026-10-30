package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.common.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @AfterEach
    void resetClock() {
        rateLimiter.setClock(Clock.systemUTC());
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
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice_01"));
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRotatesExistingSessionId() throws Exception {
        String username = newUsername();
        registerAndLogin(username);
        MockHttpSession preLoginSession = new MockHttpSession();
        String idBeforeLogin = preLoginSession.getId();

        MvcResult result = perform(json(post("/api/auth/login").session(preLoginSession),
                Map.of("email", username + "@example.com", "password", PASSWORD)));
        assertEquals(200, result.getResponse().getStatus());
        assertNotEquals(idBeforeLogin, result.getRequest().getSession().getId());
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
    void changingThePasswordLogsOutOtherSessionsButKeepsTheCurrentOne() throws Exception {
        String username = newUsername();
        MockHttpSession pc = registerAndLogin(username);
        MockHttpSession phone = login(username);

        mockMvc.perform(json(put("/api/users/me").session(pc),
                        Map.of("currentPassword", PASSWORD, "newPassword", "new-password-123")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/timeline").session(pc)).andExpect(status().isOk());
        mockMvc.perform(get("/api/timeline").session(phone)).andExpect(status().isUnauthorized());

        mockMvc.perform(json(post("/api/auth/login"),
                        Map.of("email", username + "@example.com", "password", "new-password-123")))
                .andExpect(status().isOk());
    }

    @Test
    void updateRequiresTheRightCurrentPasswordAFreeEmailAndALogin() throws Exception {
        String taken = newUsername();
        registerAndLogin(taken);
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(json(put("/api/users/me").session(session),
                        Map.of("currentPassword", "wrong-password", "email", "x@example.com")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(put("/api/users/me").session(session),
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
