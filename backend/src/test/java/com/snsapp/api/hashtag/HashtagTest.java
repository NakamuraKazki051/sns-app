package com.snsapp.api.hashtag;

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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ハッシュタグ・トレンド(F-22) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HashtagTest extends ApiTestSupport {

    /** ほかのテストのタグと混ざらないよう、毎回ちがうタグにする */
    private static String uniqueTag(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private List<String> bodies(String tag, Cookie viewer) throws Exception {
        JsonNode page = body(perform(get("/api/hashtags/" + tag + "/posts").cookie(viewer)));
        List<String> result = new ArrayList<>();
        page.get("posts").forEach(p -> result.add(p.get("body").asString()));
        return result;
    }

    @Test
    void extractsTagsTheSameWayAsTheScreen() {
        assertEquals(Set.of("旅行", "tokyo_2026", "ラーメン"),
                Hashtags.extract("#旅行 で #Tokyo_2026 に行った。＃ラーメン もおいしい #旅行"));
        // 単語の途中の #・数字だけ・# だけはタグではない
        assertEquals(Set.of(), Hashtags.extract("C# と a#b と #123 と # だけ"));
        assertEquals(Set.of("ok"), Hashtags.extract("(#ok)"));
        assertEquals("abc", Hashtags.normalize("#ABC"));
    }

    @Test
    void postsCanBeFoundByTagIgnoringCase() throws Exception {
        String tag = uniqueTag("Tag");
        Cookie alice = registerAndLogin();
        createPost(alice, "1つめ #" + tag);
        createPost(alice, "2つめ #" + tag.toUpperCase());
        createPost(alice, "タグなし");

        assertEquals(List.of("2つめ #" + tag.toUpperCase(), "1つめ #" + tag), bodies(tag.toLowerCase(), alice));
        // 先頭に # を付けて渡してもよい(URL では %23)
        assertEquals(2, body(perform(get("/api/hashtags/{tag}/posts", "#" + tag).cookie(alice))).get("posts").size());
    }

    @Test
    void japaneseTagsWork() throws Exception {
        String tag = uniqueTag("旅行");
        Cookie alice = registerAndLogin();
        createPost(alice, "#" + tag + " に行った");
        mockMvc.perform(get("/api/hashtags/{tag}/posts", tag).cookie(alice)).andExpect(status().isOk());
        assertEquals(1, body(perform(get("/api/hashtags/{tag}/posts", tag).cookie(alice))).get("posts").size());
    }

    @Test
    void editingAndDeletingKeepTagsInStep() throws Exception {
        String before = uniqueTag("before");
        String after = uniqueTag("after");
        Cookie alice = registerAndLogin();
        String id = createPost(alice, "#" + before);

        mockMvc.perform(json(patch("/api/posts/" + id).cookie(alice), Map.of("body", "#" + after)))
                .andExpect(status().isOk());
        assertEquals(List.of(), bodies(before, alice));
        assertEquals(1, bodies(after, alice).size());

        mockMvc.perform(delete("/api/posts/" + id).cookie(alice)).andExpect(status().isNoContent());
        assertEquals(List.of(), bodies(after, alice));
    }

    @Test
    void mutedPeoplesTaggedPostsAreHidden() throws Exception {
        String tag = uniqueTag("hide");
        String bobName = newUsername();
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin(bobName);
        createPost(bob, "#" + tag);
        mockMvc.perform(post("/api/users/" + bobName + "/mute").cookie(alice)).andExpect(status().isOk());
        assertEquals(List.of(), bodies(tag, alice));
    }

    @Test
    void trendsCountRecentPostsPerTag() throws Exception {
        String popular = uniqueTag("pop");
        String rare = uniqueTag("rare");
        Cookie alice = registerAndLogin();
        Cookie bob = registerAndLogin();
        createPost(alice, "#" + popular);
        createPost(bob, "#" + popular + " #" + rare);

        JsonNode trends = body(perform(get("/api/trends").cookie(alice)));
        assertTrue(trends.size() <= HashtagController.TRENDS_MAX);
        Map<String, Long> counts = new java.util.HashMap<>();
        trends.forEach(t -> counts.put(t.get("tag").asString(), t.get("postsCount").asLong()));
        assertEquals(2L, counts.get(popular.toLowerCase()));
        assertEquals(1L, counts.get(rare.toLowerCase()));
    }

    @Test
    void tooLongTagsAreRejected() throws Exception {
        mockMvc.perform(get("/api/hashtags/" + "a".repeat(51) + "/posts").cookie(registerAndLogin()))
                .andExpect(status().isBadRequest());
    }
}
