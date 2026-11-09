package com.snsapp.api.notification;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通知(F-21) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationTest extends ApiTestSupport {

    /** 新しい順に「種類:した人」 */
    private List<String> kinds(Cookie token) throws Exception {
        JsonNode page = body(perform(get("/api/notifications").cookie(token)));
        List<String> result = new ArrayList<>();
        page.get("notifications").forEach(n ->
                result.add(n.get("type").asString() + ":" + n.get("actor").get("username").asString()));
        return result;
    }

    private long unread(Cookie token) throws Exception {
        return body(perform(get("/api/notifications/unread-count").cookie(token))).get("count").asLong();
    }

    @Test
    void likesRepliesRepostsAndFollowsNotifyTheOtherPerson() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin(bobName);
        String postId = createPost(alice, "alice の投稿");

        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/posts/" + postId + "/replies").cookie(bob), Map.of("body", "いいね")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/posts/" + postId + "/repost").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + aliceName + "/follow").cookie(bob)).andExpect(status().isOk());

        assertEquals(List.of("FOLLOW:" + bobName, "REPOST:" + bobName, "REPLY:" + bobName, "LIKE:" + bobName), kinds(alice));
        mockMvc.perform(get("/api/notifications").cookie(alice))
                .andExpect(jsonPath("$.notifications[0].post").isEmpty())
                .andExpect(jsonPath("$.notifications[1].post.id").value(postId))
                .andExpect(jsonPath("$.notifications[1].post.body").value("alice の投稿"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
        assertEquals(4, unread(alice));
        assertEquals(0, unread(bob));

        mockMvc.perform(post("/api/notifications/read").cookie(alice)).andExpect(status().isNoContent());
        assertEquals(0, unread(alice));
        mockMvc.perform(get("/api/notifications").cookie(alice)).andExpect(jsonPath("$.notifications[0].read").value(true));
    }

    @Test
    void undoingRemovesTheNotificationAndRedoingDoesNotPileUp() throws Exception {
        String aliceName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin();
        String postId = createPost(alice, "本文");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
            mockMvc.perform(delete("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/users/" + aliceName + "/follow").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/users/" + aliceName + "/follow").cookie(bob)).andExpect(status().isOk());
        assertEquals(List.of(), kinds(alice));

        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
        assertEquals(1, kinds(alice).size());
    }

    @Test
    void myOwnActionsAndBlockedOrMutedPeopleDoNotNotifyMe() throws Exception {
        String bobName = newUsername();
        String carolName = newUsername();
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin(bobName);
        Cookie carol = registerAndLogin(carolName);
        String postId = createPost(alice, "本文");

        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/posts/" + postId + "/replies").cookie(alice), Map.of("body", "自分で")))
                .andExpect(status().isCreated());
        assertEquals(List.of(), kinds(alice));

        // 前からある通知も、ミュート・ブロックすると出なくなる
        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(carol)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + bobName + "/mute").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + carolName + "/block").cookie(alice)).andExpect(status().isOk());
        assertEquals(List.of(), kinds(alice));
        assertEquals(0, unread(alice));
    }

    @Test
    void deletingThePostRemovesItsNotifications() throws Exception {
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin();
        String postId = createPost(alice, "消す投稿");
        mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/posts/" + postId).cookie(alice)).andExpect(status().isNoContent());
        assertEquals(List.of(), kinds(alice));
    }

    @Test
    void pagesHoldTwentyAndContinueWithBefore() throws Exception {
        Cookie alice = registerAndLogin();
        List<Cookie> fans = new ArrayList<>();
        String postId = createPost(alice, "人気の投稿");
        for (int i = 0; i < NotificationController.PAGE_SIZE + 1; i++) {
            Cookie fan = registerAndLogin();
            fans.add(fan);
            mockMvc.perform(post("/api/posts/" + postId + "/like").cookie(fan)).andExpect(status().isOk());
        }
        JsonNode first = body(perform(get("/api/notifications").cookie(alice)));
        assertEquals(NotificationController.PAGE_SIZE, first.get("notifications").size());
        mockMvc.perform(get("/api/notifications").param("before", first.get("nextCursor").asString()).cookie(alice))
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }
}
