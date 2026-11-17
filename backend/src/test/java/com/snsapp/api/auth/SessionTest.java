package com.snsapp.api.auth;

import tools.jackson.databind.JsonNode;
import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ログイン中の端末の一覧と、選んだ端末のログアウト */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionTest extends ApiTestSupport {

    private static final String WINDOWS_CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private static final String IPHONE_SAFARI =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";

    @Autowired
    private TokenService tokenService;

    @AfterEach
    void resetClock() {
        tokenService.setClock(Clock.systemUTC());
    }

    private record Device(Cookie access, Cookie refresh) {
    }

    private Device signIn(String username, String userAgent) throws Exception {
        MvcResult result = perform(json(post("/api/auth/login"),
                Map.of("email", username + "@example.com", "password", PASSWORD))
                .header(HttpHeaders.USER_AGENT, userAgent));
        assertEquals(200, result.getResponse().getStatus());
        return new Device(result.getResponse().getCookie(CurrentUser.COOKIE_NAME),
                result.getResponse().getCookie(CurrentUser.REFRESH_COOKIE_NAME));
    }

    private String registerUser() throws Exception {
        String username = newUsername();
        mockMvc.perform(json(post("/api/auth/register"), registerParams(username))).andExpect(status().isCreated());
        return username;
    }

    private JsonNode sessions(Cookie access) throws Exception {
        MvcResult result = perform(get("/api/me/sessions").cookie(access));
        assertEquals(200, result.getResponse().getStatus());
        return body(result);
    }

    @Test
    void listsEachLoginWithItsDeviceAndMarksThisOne() throws Exception {
        String username = registerUser();
        Device pc = signIn(username, WINDOWS_CHROME);
        Device phone = signIn(username, IPHONE_SAFARI);

        JsonNode list = sessions(pc.access());
        assertEquals(2, list.size());
        // 最後に使った新しい順(後からログインしたスマホが先)
        assertEquals("Safari(iPhone)", list.get(0).get("device").asString());
        assertFalse(list.get(0).get("current").asBoolean());
        assertEquals("Chrome(Windows)", list.get(1).get("device").asString());
        assertTrue(list.get(1).get("current").asBoolean());
        assertTrue(list.get(1).has("createdAt"));
        assertTrue(list.get(1).has("lastUsedAt"));

        // ほかの人のログインは見えない
        Device other = signIn(registerUser(), WINDOWS_CHROME);
        assertEquals(1, sessions(other.access()).size());
        assertEquals(2, sessions(phone.access()).size());
    }

    @Test
    void revokingADeviceLogsItOutAtOnce() throws Exception {
        String username = registerUser();
        Device pc = signIn(username, WINDOWS_CHROME);
        Device phone = signIn(username, IPHONE_SAFARI);
        String phoneId = sessions(phone.access()).get(0).get("id").asString();

        mockMvc.perform(delete("/api/me/sessions/" + phoneId).cookie(pc.access())).andExpect(status().isNoContent());

        // スマホは、アクセストークンもリフレッシュトークンも使えない
        mockMvc.perform(get("/api/timeline").cookie(phone.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(phone.refresh())).andExpect(status().isUnauthorized());
        // PC はそのまま
        mockMvc.perform(get("/api/timeline").cookie(pc.access())).andExpect(status().isOk());
        assertEquals(1, sessions(pc.access()).size());
    }

    @Test
    void othersLoginsCannotBeRevoked() throws Exception {
        Device mine = signIn(registerUser(), WINDOWS_CHROME);
        Device theirs = signIn(registerUser(), IPHONE_SAFARI);
        String theirId = sessions(theirs.access()).get(0).get("id").asString();

        mockMvc.perform(delete("/api/me/sessions/" + theirId).cookie(mine.access())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/me/sessions/no-such-session").cookie(mine.access()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/timeline").cookie(theirs.access())).andExpect(status().isOk());
    }

    @Test
    void revokingThisDeviceIsALogout() throws Exception {
        Device pc = signIn(registerUser(), WINDOWS_CHROME);
        String id = sessions(pc.access()).get(0).get("id").asString();

        MvcResult result = perform(delete("/api/me/sessions/" + id).cookie(pc.access()));
        assertEquals(204, result.getResponse().getStatus());
        assertEquals(0, result.getResponse().getCookie(CurrentUser.COOKIE_NAME).getMaxAge());
        mockMvc.perform(get("/api/timeline").cookie(pc.access())).andExpect(status().isUnauthorized());
    }

    @Test
    void revokeOthersKeepsOnlyThisDevice() throws Exception {
        String username = registerUser();
        Device pc = signIn(username, WINDOWS_CHROME);
        Device phone = signIn(username, IPHONE_SAFARI);
        Device tablet = signIn(username, "Mozilla/5.0 (Linux; Android 15) Chrome/142.0.0.0 Mobile Safari/537.36");

        mockMvc.perform(delete("/api/me/sessions").cookie(pc.access())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/timeline").cookie(phone.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/timeline").cookie(tablet.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/sessions").cookie(pc.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void logoutAndPasswordChangeRemoveLoginsFromTheList() throws Exception {
        String username = registerUser();
        Device pc = signIn(username, WINDOWS_CHROME);
        Device phone = signIn(username, IPHONE_SAFARI);
        Device tablet = signIn(username, IPHONE_SAFARI);

        mockMvc.perform(post("/api/auth/logout").cookie(tablet.access(), tablet.refresh()))
                .andExpect(status().isNoContent());
        assertEquals(2, sessions(pc.access()).size());

        // パスワードを変えると、変えた端末の新しいログインだけが残る
        MvcResult changed = perform(json(put("/api/users/me"),
                Map.of("currentPassword", PASSWORD, "newPassword", "another-password-1"))
                .cookie(pc.access()).header(HttpHeaders.USER_AGENT, WINDOWS_CHROME));
        assertEquals(200, changed.getResponse().getStatus());
        Cookie renewed = changed.getResponse().getCookie(CurrentUser.COOKIE_NAME);
        JsonNode list = sessions(renewed);
        assertEquals(1, list.size());
        assertTrue(list.get(0).get("current").asBoolean());
        mockMvc.perform(get("/api/timeline").cookie(phone.access())).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshingMovesLastUsedForwardAndSevenIdleDaysDropTheLogin() throws Exception {
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        tokenService.setClock(Clock.fixed(start, ZoneOffset.UTC));
        String username = registerUser();
        Device pc = signIn(username, WINDOWS_CHROME);
        Device phone = signIn(username, IPHONE_SAFARI);

        // 3日後に PC だけ使う
        tokenService.setClock(Clock.fixed(start.plus(Duration.ofDays(3)), ZoneOffset.UTC));
        MvcResult refreshed = perform(post("/api/auth/refresh").cookie(pc.refresh())
                .header(HttpHeaders.USER_AGENT, WINDOWS_CHROME));
        Cookie access = refreshed.getResponse().getCookie(CurrentUser.COOKIE_NAME);
        JsonNode list = sessions(access);
        assertEquals("Chrome(Windows)", list.get(0).get("device").asString());
        assertEquals(start.plus(Duration.ofDays(3)), Instant.parse(list.get(0).get("lastUsedAt").asString()));
        assertEquals(start, Instant.parse(list.get(0).get("createdAt").asString()));

        // ログインから8日後。スマホは7日使っていないので一覧から消える
        tokenService.setClock(Clock.fixed(start.plus(Duration.ofDays(8)), ZoneOffset.UTC));
        refreshed = perform(post("/api/auth/refresh").cookie(refreshed.getResponse().getCookie(CurrentUser.REFRESH_COOKIE_NAME)));
        list = sessions(refreshed.getResponse().getCookie(CurrentUser.COOKIE_NAME));
        assertEquals(1, list.size());
        mockMvc.perform(post("/api/auth/refresh").cookie(phone.refresh())).andExpect(status().isUnauthorized());
    }

    @Test
    void userAgentsAreShortenedToBrowserAndOs() {
        assertEquals("Chrome(Windows)", UserAgents.describe(WINDOWS_CHROME));
        assertEquals("Safari(iPhone)", UserAgents.describe(IPHONE_SAFARI));
        assertEquals("Edge(Windows)", UserAgents.describe(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36 Edg/142.0.0.0"));
        assertEquals("Firefox(Mac)", UserAgents.describe(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.5; rv:140.0) Gecko/20100101 Firefox/140.0"));
        assertEquals("Chrome(Android)", UserAgents.describe(
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36"));
        assertEquals("curl", UserAgents.describe("curl/8.9.1"));
        assertEquals("不明な端末", UserAgents.describe(null));
        assertEquals("不明な端末", UserAgents.describe("something"));
    }
}
