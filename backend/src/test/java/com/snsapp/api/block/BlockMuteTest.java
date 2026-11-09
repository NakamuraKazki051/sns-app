package com.snsapp.api.block;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ブロック・ミュート(F-20) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BlockMuteTest extends ApiTestSupport {

    private List<String> bodies(String path, Cookie viewer) throws Exception {
        JsonNode page = body(perform(get(path).cookie(viewer)));
        List<String> result = new ArrayList<>();
        page.get("posts").forEach(p -> result.add(p.get("body").asString()));
        return result;
    }

    private List<String> usernames(JsonNode users) {
        List<String> result = new ArrayList<>();
        users.forEach(u -> result.add(u.get("username").asString()));
        return result;
    }

    @Test
    void blockingHidesBothSidesFromEachOtherAndRemovesFollows() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin(bobName);
        String alicePost = createPost(alice, "alice " + aliceName);
        String bobPost = createPost(bob, "bob " + bobName);
        mockMvc.perform(post("/api/users/" + bobName + "/follow").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + aliceName + "/follow").cookie(bob)).andExpect(status().isOk());

        mockMvc.perform(post("/api/users/" + bobName + "/block").cookie(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocking").value(true));

        // フォローは両方向とも外れる
        mockMvc.perform(get("/api/users/" + aliceName + "/following").cookie(alice)).andExpect(jsonPath("$.users").isEmpty());
        mockMvc.perform(get("/api/users/" + aliceName + "/followers").cookie(alice)).andExpect(jsonPath("$.users").isEmpty());

        // bob からは alice がいないように見える
        mockMvc.perform(get("/api/users/" + aliceName).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/" + alicePost).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/" + alicePost + "/like").cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/" + alicePost + "/repost").cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/posts/" + alicePost + "/replies").cookie(bob), Map.of("body", "x")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/" + aliceName + "/follow").cookie(bob)).andExpect(status().isNotFound());
        assertFalse(bodies("/api/timeline", bob).contains("alice " + aliceName));
        assertTrue(usernames(body(perform(get("/api/users").param("q", aliceName).cookie(bob))).get("users")).isEmpty());

        // alice からは bob の投稿が消えるが、解除できるようページは開ける
        assertFalse(bodies("/api/timeline", alice).contains("bob " + bobName));
        mockMvc.perform(get("/api/posts/" + bobPost).cookie(alice)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/" + bobName).cookie(alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocking").value(true));
        mockMvc.perform(get("/api/users/" + bobName + "/posts").cookie(alice)).andExpect(jsonPath("$.posts").isEmpty());
        mockMvc.perform(post("/api/users/" + bobName + "/follow").cookie(alice)).andExpect(status().isBadRequest());
        assertEquals(List.of(bobName), usernames(body(perform(get("/api/me/blocks").cookie(alice)))));

        // 解除すると元どおり見える(フォローは戻らない)
        mockMvc.perform(delete("/api/users/" + bobName + "/block").cookie(alice))
                .andExpect(jsonPath("$.blocking").value(false));
        mockMvc.perform(get("/api/posts/" + alicePost).cookie(bob)).andExpect(status().isOk());
        assertTrue(bodies("/api/timeline", alice).contains("bob " + bobName));
        mockMvc.perform(get("/api/users/" + bobName).cookie(alice)).andExpect(jsonPath("$.followedByMe").value(false));
    }

    @Test
    void repliesFromBlockedPeopleAreHidden() throws Exception {
        String bobName = newUsername();
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin(bobName);
        Cookie carol = registerAndLogin();
        String carolPost = createPost(carol, "carol の投稿");
        mockMvc.perform(json(post("/api/posts/" + carolPost + "/replies").cookie(bob), Map.of("body", "bob のリプライ")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users/" + bobName + "/block").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + carolPost + "/replies").cookie(alice)).andExpect(jsonPath("$.replies").isEmpty());
        mockMvc.perform(get("/api/posts/" + carolPost + "/replies").cookie(carol)).andExpect(jsonPath("$.replies.length()").value(1));
    }

    @Test
    void mutingHidesPostsAndRepostsOnlyFromMyTimelines() throws Exception {
        String bobName = newUsername();
        String carolName = newUsername();
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin(bobName);
        Cookie carol = registerAndLogin(carolName);
        String bobText = "bob " + bobName;
        String bobPost = createPost(bob, bobText);
        mockMvc.perform(post("/api/users/" + bobName + "/follow").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + carolName + "/follow").cookie(alice)).andExpect(status().isOk());
        // carol が bob の投稿をリポストしている
        mockMvc.perform(post("/api/posts/" + bobPost + "/repost").cookie(carol)).andExpect(status().isOk());
        assertTrue(bodies("/api/timeline/home", alice).contains(bobText));

        mockMvc.perform(post("/api/users/" + bobName + "/mute").cookie(alice))
                .andExpect(jsonPath("$.muting").value(true));

        assertFalse(bodies("/api/timeline/home", alice).contains(bobText));
        assertFalse(bodies("/api/timeline", alice).contains(bobText));
        assertFalse(bodies("/api/users/" + carolName + "/posts", alice).contains(bobText));
        mockMvc.perform(get("/api/timeline/new").param("after", "0").cookie(alice))
                .andExpect(jsonPath("$.users[?(@.username == '" + bobName + "')]").isEmpty());
        // ページと投稿そのものは見られる。フォローも続く。相手には分からない
        mockMvc.perform(get("/api/users/" + bobName).cookie(alice))
                .andExpect(jsonPath("$.muting").value(true)).andExpect(jsonPath("$.followedByMe").value(true));
        mockMvc.perform(get("/api/posts/" + bobPost).cookie(alice)).andExpect(status().isOk());
        assertTrue(bodies("/api/timeline", carol).contains(bobText));
        assertEquals(List.of(bobName), usernames(body(perform(get("/api/me/mutes").cookie(alice)))));

        mockMvc.perform(delete("/api/users/" + bobName + "/mute").cookie(alice)).andExpect(jsonPath("$.muting").value(false));
        assertTrue(bodies("/api/timeline/home", alice).contains(bobText));
    }

    @Test
    void blockingAndMutingTwiceIsTheSameAsOnceAndNotYourself() throws Exception {
        String me = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(me);
        registerAndLogin(bobName);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/users/" + bobName + "/block").cookie(alice)).andExpect(status().isOk());
            mockMvc.perform(post("/api/users/" + bobName + "/mute").cookie(alice)).andExpect(status().isOk());
        }
        assertEquals(1, body(perform(get("/api/me/blocks").cookie(alice))).size());
        mockMvc.perform(post("/api/users/" + me + "/block").cookie(alice)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/" + me + "/mute").cookie(alice)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/nobody_here_x/block").cookie(alice)).andExpect(status().isNotFound());
    }
}
