package com.snsapp.api.follow;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 鍵アカウント(F-26)。投稿は本人と承認したフォロワーにだけ見え、フォローには承認が要る */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrivateAccountTest extends ApiTestSupport {

    private void makePrivate(Cookie session, boolean value) throws Exception {
        mockMvc.perform(json(patch("/api/me/profile"), Map.of("privateAccount", value)).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privateAccount").value(value));
    }

    private List<String> timeline(Cookie session) throws Exception {
        return ids(body(perform(get("/api/timeline").cookie(session))));
    }

    private JsonNode profile(String username, Cookie viewer) throws Exception {
        return body(perform(get("/api/users/" + username).cookie(viewer)));
    }

    @Test
    void postsOfAPrivateAccountAreOnlyForItselfAndApprovedFollowers() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        Cookie bob = registerAndLogin();
        makePrivate(aliceSession, true);
        String secret = createPost(aliceSession, "フォロワーだけに #ないしょ");

        // 本人には見える
        assertTrue(timeline(aliceSession).contains(secret));
        // フォローしていない人には、どこにも出ない
        assertFalse(timeline(bob).contains(secret));
        mockMvc.perform(get("/api/posts/" + secret).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/" + secret + "/like").cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/posts/" + secret + "/replies"), Map.of("body", "見えた")).cookie(bob))
                .andExpect(status().isNotFound());
        assertEquals(0, body(perform(get("/api/users/" + alice + "/posts").cookie(bob))).get("posts").size());
        assertEquals(0, body(perform(get("/api/hashtags/ないしょ/posts").cookie(bob))).get("posts").size());
        assertEquals(0, body(perform(get("/api/users/" + alice + "/followers").cookie(bob))).get("users").size());
        assertEquals(0, body(perform(get("/api/timeline/new?after=0").cookie(bob))).get("count").asInt());

        JsonNode seen = profile(alice, bob);
        assertTrue(seen.get("privateAccount").asBoolean());
        assertFalse(seen.get("canViewPosts").asBoolean());
        // 投稿の数は出す(X と同じ)
        assertEquals(1, seen.get("postsCount").asInt());
    }

    @Test
    void followingAPrivateAccountSendsARequestThatCanBeApproved() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        makePrivate(aliceSession, true);
        String secret = createPost(aliceSession, "フォロワーだけに");

        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.requested").value(true))
                .andExpect(jsonPath("$.followersCount").value(0));
        // 何度押してもリクエストは1つ
        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(bobSession)).andExpect(jsonPath("$.requested").value(true));
        assertTrue(profile(alice, bobSession).get("followRequested").asBoolean());

        // 相手に通知と一覧
        JsonNode notifications = body(perform(get("/api/notifications").cookie(aliceSession))).get("notifications");
        assertEquals("FOLLOW_REQUEST", notifications.get(0).get("type").asString());
        assertEquals(bob, notifications.get(0).get("actor").get("username").asString());
        mockMvc.perform(get("/api/me/follow-requests").cookie(aliceSession))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value(bob));
        mockMvc.perform(get("/api/me/follow-requests/count").cookie(aliceSession)).andExpect(jsonPath("$.count").value(1));

        // 承認するとフォローになり、投稿が見える
        mockMvc.perform(post("/api/me/follow-requests/" + bob).cookie(aliceSession)).andExpect(status().isNoContent());
        JsonNode after = profile(alice, bobSession);
        assertTrue(after.get("followedByMe").asBoolean());
        assertFalse(after.get("followRequested").asBoolean());
        assertTrue(after.get("canViewPosts").asBoolean());
        mockMvc.perform(get("/api/posts/" + secret).cookie(bobSession)).andExpect(status().isOk());
        assertTrue(timeline(bobSession).contains(secret));
        assertTrue(ids(body(perform(get("/api/timeline/home").cookie(bobSession)))).contains(secret));
        // リクエストの通知は片付く
        assertEquals(0, body(perform(get("/api/me/follow-requests").cookie(aliceSession))).size());
        assertTrue(body(perform(get("/api/notifications").cookie(aliceSession))).get("notifications").isEmpty());
        mockMvc.perform(post("/api/me/follow-requests/" + bob).cookie(aliceSession)).andExpect(status().isNotFound());
    }

    @Test
    void requestsCanBeRejectedOrWithdrawn() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        Cookie carol = registerAndLogin();
        makePrivate(aliceSession, true);

        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(bobSession));
        mockMvc.perform(delete("/api/me/follow-requests/" + bob).cookie(aliceSession)).andExpect(status().isNoContent());
        assertFalse(profile(alice, bobSession).get("followRequested").asBoolean());
        assertFalse(profile(alice, bobSession).get("followedByMe").asBoolean());

        // 送った人が取り下げる
        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(carol));
        mockMvc.perform(delete("/api/users/" + alice + "/follow").cookie(carol))
                .andExpect(jsonPath("$.requested").value(false));
        assertEquals(0, body(perform(get("/api/me/follow-requests").cookie(aliceSession))).size());
        assertTrue(body(perform(get("/api/notifications").cookie(aliceSession))).get("notifications").isEmpty());
    }

    @Test
    void followersFromBeforeLockingKeepSeeingPostsAndUnlockingApprovesPendingRequests() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        Cookie friend = registerAndLogin();
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(friend)).andExpect(jsonPath("$.following").value(true));
        makePrivate(aliceSession, true);
        String post = createPost(aliceSession, "前からのフォロワーには見える");
        assertTrue(timeline(friend).contains(post));

        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(bobSession));
        makePrivate(aliceSession, false);
        assertTrue(profile(alice, bobSession).get("followedByMe").asBoolean());
        assertEquals(0, body(perform(get("/api/me/follow-requests").cookie(aliceSession))).size());
    }

    @Test
    void privatePostsCannotBeRepostedAndOldRepostsAreHiddenFromOthers() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String friend = newUsername();
        Cookie friendSession = registerAndLogin(friend);
        Cookie stranger = registerAndLogin();
        String post = createPost(aliceSession, "公開のうちにリポストされた");
        mockMvc.perform(post("/api/posts/" + post + "/repost").cookie(friendSession)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + friend + "/follow").cookie(stranger));
        assertTrue(ids(body(perform(get("/api/timeline/home").cookie(stranger)))).contains(post));

        makePrivate(aliceSession, true);
        // 鍵を掛けたら、フォロワー以外にはリポストも出ない
        assertFalse(ids(body(perform(get("/api/timeline/home").cookie(stranger)))).contains(post));
        assertFalse(ids(body(perform(get("/api/users/" + friend + "/posts").cookie(stranger)))).contains(post));
        // フォロワーでも、新しくはリポストできない
        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(friendSession));
        mockMvc.perform(post("/api/me/follow-requests/" + friend).cookie(aliceSession));
        String newer = createPost(aliceSession, "鍵を掛けてから");
        mockMvc.perform(post("/api/posts/" + newer + "/repost").cookie(friendSession)).andExpect(status().isBadRequest());
    }

    @Test
    void repliesOfAPrivateAccountAreHiddenFromNonFollowers() throws Exception {
        Cookie alice = registerAndLogin();
        Cookie locked = registerAndLogin();
        Cookie stranger = registerAndLogin();
        makePrivate(locked, true);
        String post = createPost(alice, "公開の投稿");
        mockMvc.perform(json(post("/api/posts/" + post + "/replies"), Map.of("body", "鍵からのリプライ")).cookie(locked))
                .andExpect(status().isCreated());

        assertEquals(0, body(perform(get("/api/posts/" + post + "/replies").cookie(stranger))).get("replies").size());
    }

    @Test
    void blockingRemovesPendingRequests() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        makePrivate(aliceSession, true);
        mockMvc.perform(post("/api/users/" + alice + "/follow").cookie(bobSession));
        mockMvc.perform(post("/api/users/" + bob + "/block").cookie(aliceSession)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + bob + "/block").cookie(aliceSession));
        assertEquals(0, body(perform(get("/api/me/follow-requests").cookie(aliceSession))).size());
    }
}
