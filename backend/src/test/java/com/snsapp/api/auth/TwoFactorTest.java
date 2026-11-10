package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 2段階認証(F-24) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TwoFactorTest extends ApiTestSupport {

    @Autowired
    private TwoFactorService service;

    private Instant now = Instant.parse("2026-11-10T00:00:00Z");

    @AfterEach
    void resetClock() {
        service.setClock(Clock.systemUTC());
    }

    private void at(Instant instant) {
        now = instant;
        service.setClock(Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void later(Duration duration) {
        at(now.plus(duration));
    }

    private String codeNow(String secret) {
        return Totp.code(secret, Totp.stepAt(now));
    }

    /** 設定を始めて、秘密の鍵を返す */
    private String setup(Cookie session) throws Exception {
        JsonNode body = body(perform(post("/api/me/two-factor/setup").cookie(session)));
        String secret = body.get("secret").asString();
        assertTrue(body.get("uri").asString().startsWith("otpauth://totp/RaiseLine:"), body.toString());
        return secret;
    }

    /** 有効にして、リカバリーコードを返す */
    private List<String> enable(Cookie session, String secret) throws Exception {
        JsonNode body = body(perform(json(post("/api/me/two-factor/enable").cookie(session), Map.of("code", codeNow(secret)))));
        List<String> codes = new ArrayList<>();
        body.get("recoveryCodes").forEach(c -> codes.add(c.asString()));
        return codes;
    }

    private ResultActions login(String username, String code) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("email", username + "@example.com");
        params.put("password", PASSWORD);
        if (code != null) {
            params.put("code", code);
        }
        return mockMvc.perform(json(post("/api/auth/login"), params));
    }

    @Test
    void totpMatchesTheRfcTestVector() {
        // RFC 6238 の付録 B(SHA-1、鍵 "12345678901234567890")。8桁の下6桁
        String secret = Totp.base32("12345678901234567890".getBytes());
        assertEquals("287082", Totp.code(secret, Totp.stepAt(Instant.ofEpochSecond(59))));
        assertEquals("005924", Totp.code(secret, Totp.stepAt(Instant.ofEpochSecond(1234567890))));
    }

    @Test
    void afterEnablingLoginNeedsTheCodeAndTheSameCodeCannotBeReused() throws Exception {
        at(now);
        String name = newUsername();
        Cookie session = registerAndLogin(name);
        String secret = setup(session);
        // 有効にするまでは、コード無しでログインできる
        login(name, null).andExpect(status().isOk());
        List<String> recovery = enable(session, secret);
        assertEquals(TwoFactorService.RECOVERY_CODES, recovery.size());
        mockMvc.perform(get("/api/me/two-factor").cookie(session))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10));

        login(name, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthController.TWO_FACTOR_REQUIRED));
        login(name, "000000").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").doesNotExist());
        // 有効にするのに使ったコードは、もう使えない
        login(name, codeNow(secret)).andExpect(status().isUnauthorized());

        later(Duration.ofSeconds(30));
        String code = codeNow(secret);
        login(name, code.substring(0, 3) + " " + code.substring(3)).andExpect(status().isOk());
        login(name, code).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordMustBeRightBeforeTheCodeIsAsked() throws Exception {
        at(now);
        String name = newUsername();
        Cookie session = registerAndLogin(name);
        enable(session, setup(session));
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", name + "@example.com", "password", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void recoveryCodesWorkOnceEach() throws Exception {
        at(now);
        String name = newUsername();
        Cookie session = registerAndLogin(name);
        List<String> recovery = enable(session, setup(session));
        assertTrue(recovery.getFirst().matches("[a-z2-9]{4}-[a-z2-9]{4}"), recovery.getFirst());

        login(name, recovery.getFirst().toUpperCase()).andExpect(status().isOk());
        login(name, recovery.getFirst()).andExpect(status().isUnauthorized());
        login(name, recovery.get(1).replace("-", "")).andExpect(status().isOk());
        mockMvc.perform(get("/api/me/two-factor").cookie(session)).andExpect(jsonPath("$.recoveryCodesLeft").value(8));

        // 作り直すと前のものは使えない
        later(Duration.ofSeconds(30));
        JsonNode fresh = body(perform(json(post("/api/me/two-factor/recovery-codes").cookie(session),
                Map.of("code", recovery.get(2)))));
        assertEquals(10, fresh.get("recoveryCodes").size());
        login(name, recovery.get(3)).andExpect(status().isUnauthorized());
        login(name, fresh.get("recoveryCodes").get(0).asString()).andExpect(status().isOk());
    }

    @Test
    void enablingNeedsSetupAndTheRightCode() throws Exception {
        at(now);
        Cookie session = registerAndLogin();
        mockMvc.perform(json(post("/api/me/two-factor/enable").cookie(session), Map.of("code", "123456")))
                .andExpect(status().isBadRequest());
        String secret = setup(session);
        mockMvc.perform(json(post("/api/me/two-factor/enable").cookie(session), Map.of("code", "12345")))
                .andExpect(status().isBadRequest());
        // 時計が大きくずれたコードは通らない
        mockMvc.perform(json(post("/api/me/two-factor/enable").cookie(session),
                        Map.of("code", Totp.code(secret, Totp.stepAt(now) - 3))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/me/two-factor").cookie(session)).andExpect(jsonPath("$.enabled").value(false));
        // 前後30秒のずれまでは通る
        mockMvc.perform(json(post("/api/me/two-factor/enable").cookie(session),
                        Map.of("code", Totp.code(secret, Totp.stepAt(now) - 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/me/two-factor/setup").cookie(session)).andExpect(status().isConflict());
    }

    @Test
    void disablingNeedsThePasswordAndACode() throws Exception {
        at(now);
        String name = newUsername();
        Cookie session = registerAndLogin(name);
        String secret = setup(session);
        enable(session, secret);
        later(Duration.ofSeconds(30));

        mockMvc.perform(json(delete("/api/me/two-factor").cookie(session), Map.of("password", "wrong-password", "code", codeNow(secret))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(delete("/api/me/two-factor").cookie(session), Map.of("password", PASSWORD, "code", "000000")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(delete("/api/me/two-factor").cookie(session), Map.of("password", PASSWORD, "code", codeNow(secret))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/two-factor").cookie(session))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(0));
        login(name, null).andExpect(status().isOk());
        mockMvc.perform(json(delete("/api/me/two-factor").cookie(session), Map.of("password", PASSWORD, "code", "000000")))
                .andExpect(status().isConflict());
    }

    @Test
    void guessingCodesIsLimited() throws Exception {
        at(now);
        Cookie session = registerAndLogin();
        setup(session);
        for (int i = 0; i < TwoFactorController.ATTEMPTS; i++) {
            mockMvc.perform(json(post("/api/me/two-factor/enable").cookie(session), Map.of("code", "000000")))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(json(post("/api/me/two-factor/enable").cookie(session), Map.of("code", "000000")))
                .andExpect(status().isTooManyRequests());
    }
}
