package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.common.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.email-verification.required=true")
@AutoConfigureMockMvc
class EmailVerificationTest extends ApiTestSupport {

    @MockitoBean
    private VerificationMailer mailer;

    @Autowired
    private EmailVerificationService service;

    @AfterEach
    void resetClock() {
        service.setClock(Clock.systemUTC());
    }

    private void advance(Duration duration) {
        service.setClock(Clock.fixed(Instant.now().plus(duration), ZoneOffset.UTC));
    }

    private ResultActions register(Map<String, Object> params) throws Exception {
        return mockMvc.perform(json(post("/api/auth/register"), params));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", password)));
    }

    private ResultActions verifyToken(String token) throws Exception {
        return mockMvc.perform(json(post("/api/auth/verify-email"), Map.of("token", token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(json(post("/api/auth/resend-verification"), Map.of("email", email)));
    }

    /** email に送った確認リンクのうち、times 通目のトークン */
    private String tokenSentTo(String email, int count) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(count)).send(eq(email), link.capture());
        List<String> links = link.getAllValues();
        String last = links.getLast();
        assertTrue(last.startsWith("http://localhost:5173/verify-email?token="), last);
        return last.substring(last.indexOf('=') + 1);
    }

    private String registerAndVerify(Map<String, Object> params) throws Exception {
        String email = (String) params.get("email");
        register(params).andExpect(status().isCreated());
        verifyToken(tokenSentTo(email, 1)).andExpect(status().isNoContent());
        return email;
    }

    @Test
    void cannotLogInUntilTheLinkIsOpened() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        String email = (String) params.get("email");
        register(params).andExpect(status().isCreated());

        login(email, PASSWORD).andExpect(status().isForbidden());

        verifyToken(tokenSentTo(email, 1)).andExpect(status().isNoContent());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordDoesNotRevealThatTheAddressIsUnverified() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        register(params).andExpect(status().isCreated());

        login((String) params.get("email"), "wrong-password").andExpect(status().isUnauthorized());
    }

    @Test
    void aLinkWorksOnlyOnce() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        String email = (String) params.get("email");
        register(params).andExpect(status().isCreated());
        String token = tokenSentTo(email, 1);

        verifyToken(token).andExpect(status().isNoContent());
        verifyToken(token).andExpect(status().isBadRequest());
    }

    @Test
    void aMadeUpLinkIsRefused() throws Exception {
        verifyToken("not-a-real-token").andExpect(status().isBadRequest());
    }

    @Test
    void aLinkExpiresAfter24Hours() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        String email = (String) params.get("email");
        register(params).andExpect(status().isCreated());
        String token = tokenSentTo(email, 1);

        advance(Duration.ofHours(25));

        verifyToken(token).andExpect(status().isBadRequest());
    }

    @Test
    void resendingReplacesTheOldLink() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        String email = (String) params.get("email");
        register(params).andExpect(status().isCreated());
        String oldToken = tokenSentTo(email, 1);

        resend(email).andExpect(status().isNoContent());
        String newToken = tokenSentTo(email, 2);

        verifyToken(oldToken).andExpect(status().isBadRequest());
        verifyToken(newToken).andExpect(status().isNoContent());
    }

    @Test
    void resendSendsNothingForUnknownOrVerifiedAddresses() throws Exception {
        String unknown = newUsername() + "@example.com";
        resend(unknown).andExpect(status().isNoContent());
        verify(mailer, never()).send(eq(unknown), any());

        String verified = registerAndVerify(registerParams(newUsername()));
        resend(verified).andExpect(status().isNoContent());
        verify(mailer, times(1)).send(eq(verified), any());
    }

    @Test
    void resendIsLimitedPerAddress() throws Exception {
        String email = newUsername() + "@example.com";
        for (int i = 0; i < 3; i++) {
            resend(email).andExpect(status().isNoContent());
        }
        resend(email).andExpect(status().isTooManyRequests());
    }

    @Test
    void resendHidesAMailFailure() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        String email = (String) params.get("email");
        register(params).andExpect(status().isCreated());
        doThrow(ApiException.serviceUnavailable("送れません")).when(mailer).send(eq(email), any());

        resend(email).andExpect(status().isNoContent());
    }

    @Test
    void aFailedMailDoesNotLeaveAnAccountBehind() throws Exception {
        Map<String, Object> params = registerParams(newUsername());
        String email = (String) params.get("email");
        doThrow(ApiException.serviceUnavailable("送れません")).when(mailer).send(eq(email), any());

        register(params).andExpect(status().isServiceUnavailable());

        doNothing().when(mailer).send(eq(email), any());
        register(params).andExpect(status().isCreated());
    }

    @Test
    void anAbandonedRegistrationFreesTheAddressAfter24Hours() throws Exception {
        Map<String, Object> first = registerParams(newUsername());
        String email = (String) first.get("email");
        register(first).andExpect(status().isCreated());

        // 期限内は本人かもしれないので、使用中のまま
        register(first).andExpect(status().isConflict());

        advance(Duration.ofHours(25));
        register(first).andExpect(status().isCreated());
        tokenSentTo(email, 2);
    }

    @Test
    void aRegistrationIsHiddenUntilTheLinkIsOpened() throws Exception {
        MockHttpSession viewer = loggedIn(registerAndVerify(registerParams(newUsername())));
        String username = newUsername();
        Map<String, Object> params = registerParams(username);
        register(params).andExpect(status().isCreated());

        // 見えているとフォローでき、フォローされた登録は外部キーのせいで消せなくなる(そのあとの新規登録が全部失敗する)
        mockMvc.perform(get("/api/users").param("q", username).session(viewer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.users").isEmpty());
        mockMvc.perform(get("/api/users/" + username).session(viewer)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/" + username + "/follow").session(viewer)).andExpect(status().isNotFound());

        verifyToken(tokenSentTo((String) params.get("email"), 1)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users").param("q", username).session(viewer))
                .andExpect(jsonPath("$.users[0].username").value(username));
        mockMvc.perform(post("/api/users/" + username + "/follow").session(viewer)).andExpect(status().isOk());
    }

    private MockHttpSession loggedIn(String email) throws Exception {
        return (MockHttpSession) login(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions changeEmail(MockHttpSession session, String email) throws Exception {
        return mockMvc.perform(json(put("/api/users/me"), Map.of("email", email, "currentPassword", PASSWORD))
                .session(session));
    }

    @Test
    void theNewAddressIsUsedOnlyAfterItsLinkIsOpened() throws Exception {
        String oldEmail = registerAndVerify(registerParams(newUsername()));
        MockHttpSession session = loggedIn(oldEmail);
        String newEmail = newUsername() + "@example.com";

        changeEmail(session, newEmail).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(oldEmail));
        login(oldEmail, PASSWORD).andExpect(status().isOk());

        verifyToken(tokenSentTo(newEmail, 1)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.email").value(newEmail));
        login(newEmail, PASSWORD).andExpect(status().isOk());
        login(oldEmail, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void theNewAddressCanBeTakenBeforeItsLinkIsOpened() throws Exception {
        String email = registerAndVerify(registerParams(newUsername()));
        String wanted = newUsername() + "@example.com";
        changeEmail(loggedIn(email), wanted).andExpect(status().isOk());
        String token = tokenSentTo(wanted, 1);

        Map<String, Object> other = registerParams(newUsername());
        other.put("email", wanted);
        register(other).andExpect(status().isCreated());

        verifyToken(token).andExpect(status().isConflict());
    }

    @Test
    void emailChangesAreLimited() throws Exception {
        MockHttpSession session = loggedIn(registerAndVerify(registerParams(newUsername())));
        for (int i = 0; i < 3; i++) {
            changeEmail(session, newUsername() + "@example.com").andExpect(status().isOk());
        }
        changeEmail(session, newUsername() + "@example.com").andExpect(status().isTooManyRequests());
    }
}
