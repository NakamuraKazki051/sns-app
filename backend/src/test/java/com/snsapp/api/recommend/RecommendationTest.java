package com.snsapp.api.recommend;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** おすすめ(F-27) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationTest extends ApiTestSupport {

    private void follow(Cookie who, String whom) throws Exception {
        mockMvc.perform(post("/api/users/" + whom + "/follow").cookie(who));
    }

    private List<String> recommendedUsernames(Cookie me) throws Exception {
        List<String> names = new ArrayList<>();
        body(perform(get("/api/recommendations/users").cookie(me))).forEach(u -> names.add(u.get("username").asString()));
        return names;
    }

    @Test
    void peopleFollowedByTheFollowedComeFirst() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        String carol = newUsername();
        Cookie carolSession = registerAndLogin(carol);
        String dave = newUsername();
        registerAndLogin(dave);
        String erin = newUsername();
        registerAndLogin(erin);
        follow(aliceSession, bob);
        follow(aliceSession, carol);
        follow(bobSession, dave);
        follow(carolSession, dave);
        follow(carolSession, erin);

        JsonNode list = body(perform(get("/api/recommendations/users").cookie(aliceSession)));
        assertTrue(list.size() <= RecommendationController.USERS_MAX);
        assertEquals(dave, list.get(0).get("username").asString());
        assertEquals("FOLLOWED_BY_FOLLOWING", list.get(0).get("reason").asString());
        assertEquals(2, list.get(0).get("mutualCount").asInt());
        assertEquals(erin, list.get(1).get("username").asString());
        assertEquals(1, list.get(1).get("mutualCount").asInt());
        // 自分とフォロー済みの人は出さない
        List<String> names = recommendedUsernames(aliceSession);
        assertFalse(names.contains(alice));
        assertFalse(names.contains(bob));
        assertFalse(names.contains(carol));
    }

    @Test
    void blockedAndRequestedPeopleAreNotRecommended() throws Exception {
        Cookie alice = registerAndLogin();
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        String blocked = newUsername();
        registerAndLogin(blocked);
        String locked = newUsername();
        Cookie lockedSession = registerAndLogin(locked);
        mockMvc.perform(json(patch("/api/me/profile"), Map.of("privateAccount", true)).cookie(lockedSession));
        follow(alice, bob);
        follow(bobSession, blocked);
        follow(bobSession, locked);
        mockMvc.perform(post("/api/users/" + blocked + "/block").cookie(alice));
        follow(alice, locked);

        List<String> names = recommendedUsernames(alice);
        assertFalse(names.contains(blocked));
        assertFalse(names.contains(locked));
    }

    @Test
    void someoneWithNoFollowsStillGetsRecommendations() throws Exception {
        Cookie newcomer = registerAndLogin();
        registerAndLogin();
        assertFalse(recommendedUsernames(newcomer).isEmpty());
    }

    @Test
    void recommendedPostsAreOrderedByReactions() throws Exception {
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin();
        Cookie carol = registerAndLogin();
        Cookie viewer = registerAndLogin();
        String quiet = createPost(alice, "反応なし");
        String liked = createPost(alice, "いいね2つ");
        String reposted = createPost(bob, "リポスト1つ");
        String mine = createPost(viewer, "自分の投稿");
        mockMvc.perform(post("/api/posts/" + liked + "/like").cookie(bob));
        mockMvc.perform(post("/api/posts/" + liked + "/like").cookie(carol));
        mockMvc.perform(post("/api/posts/" + reposted + "/repost").cookie(carol));
        mockMvc.perform(post("/api/posts/" + reposted + "/like").cookie(alice));
        // リポスト(2点) + いいね(1点) = 3点 > いいね2つ(2点) > 0点
        JsonNode page = body(perform(get("/api/timeline/recommended").cookie(viewer)));
        List<String> ids = ids(page);
        assertTrue(ids.indexOf(reposted) < ids.indexOf(liked));
        assertTrue(ids.indexOf(liked) < ids.indexOf(quiet));
        assertFalse(ids.contains(mine));
        assertFalse(page.get("hasMore").asBoolean());
        assertTrue(ids.size() <= RecommendationController.POSTS_MAX);
    }

    @Test
    void hiddenPeoplesPostsAreNotRecommended() throws Exception {
        Cookie viewer = registerAndLogin();
        String muted = newUsername();
        Cookie mutedSession = registerAndLogin(muted);
        Cookie locked = registerAndLogin();
        mockMvc.perform(json(patch("/api/me/profile"), Map.of("privateAccount", true)).cookie(locked));
        String mutedPost = createPost(mutedSession, "ミュート中");
        String lockedPost = createPost(locked, "鍵アカウント");
        mockMvc.perform(post("/api/users/" + muted + "/mute").cookie(viewer));

        List<String> ids = ids(body(perform(get("/api/timeline/recommended").cookie(viewer))));
        assertFalse(ids.contains(mutedPost));
        assertFalse(ids.contains(lockedPost));
    }
}
