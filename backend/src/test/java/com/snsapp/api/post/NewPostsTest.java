package com.snsapp.api.post;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import jakarta.servlet.http.Cookie;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 新着のお知らせ(「新しい投稿 N件」) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NewPostsTest extends ApiTestSupport {

    private JsonNode newPosts(String path, Cookie session, String after) throws Exception {
        var result = perform(get(path).param("after", after).cookie(session));
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return body(result);
    }

    @Test
    void countsOnlyOthersPostsNewerThanAfter() throws Exception {
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin();
        Cookie carol = registerAndLogin();
        String top = createPost(alice, "ここまで見た");

        assertEquals(0, newPosts("/api/timeline/new", alice, top).get("count").asInt());

        createPost(alice, "自分の投稿は数えない");
        createPost(bob, "1");
        createPost(bob, "2");
        createPost(carol, "3");

        JsonNode result = newPosts("/api/timeline/new", alice, top);
        assertEquals(3, result.get("count").asInt());
        // 投稿者は重複なしで、新しい順
        assertEquals(2, result.get("users").size());
        JsonNode bobsView = newPosts("/api/timeline/new", bob, top);
        assertEquals(2, bobsView.get("count").asInt());
    }

    @Test
    void homeCountsOnlyFolloweesPosts() throws Exception {
        String bobName = newUsername();
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin(bobName);
        Cookie stranger = registerAndLogin();
        String top = createPost(alice, "ここまで見た");

        // まだ誰もフォローしていない
        createPost(bob, "フォロー前");
        assertEquals(0, newPosts("/api/timeline/home/new", alice, top).get("count").asInt());

        perform(post("/api/users/" + bobName + "/follow").cookie(alice));
        createPost(stranger, "フォローしていない人");
        createPost(alice, "自分");
        JsonNode result = newPosts("/api/timeline/home/new", alice, top);
        assertEquals(1, result.get("count").asInt());
        assertEquals(bobName, result.get("users").get(0).get("username").asString());
    }

    @Test
    void countStopsAtTheMax() throws Exception {
        Cookie alice = registerAndLogin();
        String top = createPost(alice, "ここまで見た");
        // 連投の制限にかからないよう、何人かで投稿する
        for (int i = 0; i <= PostController.NEW_POSTS_MAX / PostController.POSTS_PER_WINDOW; i++) {
            Cookie someone = registerAndLogin();
            for (int j = 0; j < PostController.POSTS_PER_WINDOW; j++) {
                createPost(someone, "新着 " + i + "-" + j);
            }
        }
        JsonNode result = newPosts("/api/timeline/new", alice, top);
        assertEquals(PostController.NEW_POSTS_MAX, result.get("count").asInt());
        assertEquals(3, result.get("users").size());
    }

    @Test
    void needsLoginAndAfter() throws Exception {
        Cookie alice = registerAndLogin();
        assertEquals(401, perform(get("/api/timeline/new").param("after", "0")).getResponse().getStatus());
        assertEquals(400, perform(get("/api/timeline/new").cookie(alice)).getResponse().getStatus());
    }
}
