package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** パスワードの再設定(F-18) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetTest extends ApiTestSupport {

    private static final String NEW_PASSWORD = "brand-new-password";

    @MockitoBean
    private VerificationMailer mailer;

    @Autowired
    private PasswordResetService service;

    @AfterEach
    void resetClock() {
        service.setClock(Clock.systemUTC());
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(json(post("/api/auth/forgot-password"), Map.of("email", email)));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(json(post("/api/auth/reset-password"), Map.of("token", token, "newPassword", password)));
    }

    private ResultActions loginWith(String email, String password) throws Exception {
        return mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", password)));
    }

    /** count 通目までに送ったうち、最後のリンクのトークン */
    private String tokenSentTo(String email, int count) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(count)).sendPasswordReset(eq(email), link.capture());
        String last = link.getAllValues().getLast();
        assertTrue(last.startsWith("http://localhost:5173/reset-password?token="), last);
        return last.substring(last.indexOf("token=") + "token=".length());
    }

    @Test
    void theLinkSetsANewPasswordAndLogsOutEveryDevice() throws Exception {
        String name = newUsername();
        String email = name + "@example.com";
        Cookie device = registerAndLogin(name);

        forgot(email.toUpperCase()).andExpect(status().isNoContent());
        reset(tokenSentTo(email, 1), NEW_PASSWORD).andExpect(status().isNoContent());

        loginWith(email, PASSWORD).andExpect(status().isUnauthorized());
        loginWith(email, NEW_PASSWORD).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(device)).andExpect(status().isUnauthorized());
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        String email = newUsername() + "@example.com";
        registerAndLogin(email.substring(0, email.indexOf('@')));
        forgot(email).andExpect(status().isNoContent());
        String token = tokenSentTo(email, 1);
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(token, "another-password-1").andExpect(status().isBadRequest());
    }

    @Test
    void aNewLinkReplacesTheOldOne() throws Exception {
        String name = newUsername();
        String email = name + "@example.com";
        registerAndLogin(name);
        forgot(email).andExpect(status().isNoContent());
        String old = tokenSentTo(email, 1);
        forgot(email).andExpect(status().isNoContent());
        String latest = tokenSentTo(email, 2);

        reset(old, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(latest, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void theLinkExpiresAfterAnHour() throws Exception {
        String name = newUsername();
        String email = name + "@example.com";
        registerAndLogin(name);
        forgot(email).andExpect(status().isNoContent());
        String token = tokenSentTo(email, 1);

        service.setClock(Clock.fixed(Instant.now().plus(Duration.ofMinutes(61)), ZoneOffset.UTC));
        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest());
        loginWith(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownAddressesGetTheSameAnswerAndNoMail() throws Exception {
        String unknown = newUsername() + "@example.com";
        forgot(unknown).andExpect(status().isNoContent());
        verify(mailer, never()).sendPasswordReset(eq(unknown), any());
    }

    @Test
    void wrongTokensAndWeakPasswordsAreRejected() throws Exception {
        reset("not-a-real-token", NEW_PASSWORD).andExpect(status().isBadRequest());
        String name = newUsername();
        String email = name + "@example.com";
        registerAndLogin(name);
        forgot(email).andExpect(status().isNoContent());
        reset(tokenSentTo(email, 1), "short").andExpect(status().isBadRequest());
    }

    @Test
    void tooManyRequestsForOneAddressAreRefused() throws Exception {
        String email = newUsername() + "@example.com";
        for (int i = 0; i < PasswordResetController.REQUESTS_PER_EMAIL; i++) {
            forgot(email).andExpect(status().isNoContent());
        }
        forgot(email).andExpect(status().isTooManyRequests());
    }
}
