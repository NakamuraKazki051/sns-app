package com.snsapp.api.reply;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.post.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReplyControllerTest extends ApiTestSupport {

    @Autowired
    private ReplyRepository replyRepository;

    @Autowired
    private PostRepository postRepository;

    private MvcResult reply(MockHttpSession session, String postId, Object body) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("body", body);
        return perform(json(post("/api/posts/" + postId + "/replies").session(session), params));
    }

    @Test
    void repliesAreListedOldestFirstAndCountedOnThePost() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String bobName = newUsername();
        MockHttpSession bob = registerAndLogin(bobName);
        String postId = createPost(alice, "元の投稿");

        assertEquals(201, reply(bob, postId, "  1つめ\n改行つき  ").getResponse().getStatus());
        assertEquals(201, reply(alice, postId, "2つめ").getResponse().getStatus());

        mockMvc.perform(get("/api/posts/" + postId + "/replies").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replies.length()").value(2))
                .andExpect(jsonPath("$.replies[0].body").value("1つめ\n改行つき"))
                .andExpect(jsonPath("$.replies[0].user.username").value(bobName))
                .andExpect(jsonPath("$.replies[0].postId").value(postId))
                .andExpect(jsonPath("$.replies[1].body").value("2つめ"))
                .andExpect(jsonPath("$.hasMore").value(false));
        mockMvc.perform(get("/api/posts/" + postId).session(bob))
                .andExpect(jsonPath("$.repliesCount").value(2));
        mockMvc.perform(get("/api/timeline").session(bob))
                .andExpect(jsonPath("$.posts[0].repliesCount").value(2));
    }

    @Test
    void repliesArePagedWithoutDuplicatesOrGaps() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String postId = createPost(alice, "人気の投稿");
        var post = postRepository.findById(Long.valueOf(postId)).orElseThrow();
        var user = post.getUser();
        for (int i = 0; i < ReplyController.PAGE_SIZE * 2 + 3; i++) {
            replyRepository.save(new Reply(post, user, "リプライ " + i));
        }

        List<String> seen = new ArrayList<>();
        String after = null;
        while (true) {
            var request = get("/api/posts/" + postId + "/replies").session(alice);
            if (after != null) {
                request.param("after", after);
            }
            JsonNode page = body(perform(request));
            page.get("replies").forEach(r -> seen.add(r.get("body").asString()));
            if (!page.get("hasMore").asBoolean()) {
                break;
            }
            after = page.get("replies").get(page.get("replies").size() - 1).get("id").asString();
        }
        assertEquals(ReplyController.PAGE_SIZE * 2 + 3, seen.size());
        for (int i = 0; i < seen.size(); i++) {
            assertEquals("リプライ " + i, seen.get(i));
        }
    }

    @Test
    void replyBodyFollowsThePostRules() throws Exception {
        MockHttpSession session = registerAndLogin();
        String postId = createPost(session, "投稿");
        assertEquals(400, reply(session, postId, "   ").getResponse().getStatus());
        assertEquals(400, reply(session, postId, null).getResponse().getStatus());
        assertEquals(400, reply(session, postId, "😀".repeat(141)).getResponse().getStatus());
        assertEquals(201, reply(session, postId, "😀".repeat(140)).getResponse().getStatus());
    }

    @Test
    void replyingToAMissingPostIsNotFound() throws Exception {
        MockHttpSession session = registerAndLogin();
        assertEquals(404, reply(session, "999999", "どこ？").getResponse().getStatus());
        mockMvc.perform(get("/api/posts/999999/replies").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/999999").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAuthorCanDeleteAReply() throws Exception {
        MockHttpSession alice = registerAndLogin();
        MockHttpSession bob = registerAndLogin();
        String postId = createPost(alice, "投稿");
        String replyId = body(reply(bob, postId, "ボブのリプライ")).get("id").asString();

        // 投稿の持ち主でも、他人のリプライは消せない
        mockMvc.perform(delete("/api/replies/" + replyId).session(alice)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/replies/" + replyId).session(bob)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/replies/" + replyId).session(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/" + postId).session(alice)).andExpect(jsonPath("$.repliesCount").value(0));
    }

    @Test
    void deletingAPostAlsoDeletesItsReplies() throws Exception {
        MockHttpSession alice = registerAndLogin();
        MockHttpSession bob = registerAndLogin();
        String postId = createPost(alice, "消す投稿");
        reply(bob, postId, "リプライ");
        long before = replyRepository.count();

        mockMvc.perform(delete("/api/posts/" + postId).session(alice)).andExpect(status().isNoContent());
        assertEquals(before - 1, replyRepository.count());
    }

    @Test
    void repliesAreRateLimitedSeparatelyFromPosts() throws Exception {
        MockHttpSession session = registerAndLogin();
        String postId = createPost(session, "投稿");
        for (int i = 0; i < 10; i++) {
            assertEquals(201, reply(session, postId, "連投 " + i).getResponse().getStatus());
        }
        assertEquals(429, reply(session, postId, "11回目").getResponse().getStatus());
        // リプライの上限に達していても、投稿はできる
        createPost(session, "投稿は別枠");
    }

    @Test
    void repliesRequireLogin() throws Exception {
        MockHttpSession session = registerAndLogin();
        String postId = createPost(session, "投稿");
        mockMvc.perform(get("/api/posts/" + postId + "/replies")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/posts/" + postId + "/replies"), Map.of("body", "x")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/replies/1")).andExpect(status().isUnauthorized());
    }
}
