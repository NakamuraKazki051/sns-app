package com.snsapp.api.post;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.common.RateLimiter;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerTest extends ApiTestSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RateLimiter rateLimiter;

    @AfterEach
    void resetClock() {
        rateLimiter.setClock(Clock.systemUTC());
    }

    /** 投稿を API を通さずにまとめて作る(連投制限にかからないように) */
    private void insertPosts(String username, int count) {
        User user = userRepository.findByUsername(username).orElseThrow();
        for (int i = 0; i < count; i++) {
            postRepository.save(new Post(user, "投稿 " + i));
        }
    }

    private List<String> allIdsNewestFirst() {
        return postRepository.findAll().stream()
                .map(Post::getId).sorted((a, b) -> Long.compare(b, a)).map(String::valueOf).toList();
    }

    @Test
    void postsAppearAtTheTopOfTheTimelineWithTheirAuthor() throws Exception {
        String username = newUsername();
        MockHttpSession session = registerAndLogin(username);
        createPost(session, "1つ目");
        String id = createPost(session, "2つ目\n改行あり");

        mockMvc.perform(get("/api/timeline").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts[0].id").value(id))
                .andExpect(jsonPath("$.posts[0].body").value("2つ目\n改行あり"))
                .andExpect(jsonPath("$.posts[0].createdAt").exists())
                .andExpect(jsonPath("$.posts[0].user.username").value(username))
                .andExpect(jsonPath("$.posts[0].user.displayName").value("表示名 " + username))
                .andExpect(jsonPath("$.posts[0].user.email").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void bodyIs1To140CharactersCountedAsTheyLook() throws Exception {
        MockHttpSession session = registerAndLogin();
        createPost(session, "あ".repeat(140));
        assertEquals(400, createPostResult(session, "あ".repeat(141)).getResponse().getStatus());

        // 家族の絵文字(人4人を ZWJ でつないだもの)は7コードポイントだが、見た目は1字
        String family = "👨‍👩‍👧‍👦";
        createPost(session, family.repeat(140));
        assertEquals(400, createPostResult(session, family.repeat(141)).getResponse().getStatus());
        // 国旗(2コードポイント)と肌の色つき(2コードポイント)も1字
        createPost(session, "🇯🇵".repeat(70) + "👍🏽".repeat(70));

        // 前後の空白は数えずに取り除く
        String id = createPost(session, "  前後の空白  \n");
        assertEquals("前後の空白", postRepository.findById(Long.valueOf(id)).orElseThrow().getBody());
    }

    @Test
    void rejectsEmptyWhitespaceOnlyAndHugeBodies() throws Exception {
        MockHttpSession session = registerAndLogin();
        for (String bad : Arrays.asList("", "   \n  ", null)) {
            assertEquals(400, createPostResult(session, bad).getResponse().getStatus());
        }
        // 結合文字を大量に重ねると見た目は1字でも巨大になる
        assertEquals(400, createPostResult(session, "a" + "́".repeat(5000)).getResponse().getStatus());
    }

    @Test
    void moreThan10PostsAMinuteAreRefusedPerUser() throws Exception {
        MockHttpSession session = registerAndLogin();
        for (int i = 0; i < PostController.POSTS_PER_WINDOW; i++) {
            createPost(session, "連投 " + i);
        }
        MvcResult refused = createPostResult(session, "11回目");
        assertEquals(429, refused.getResponse().getStatus());
        assertEquals("投稿が多すぎます。1分ほど待ってからもう一度お試しください", body(refused).get("message").asString());

        // 他の人は投稿できる
        createPost(registerAndLogin(), "別の人");

        rateLimiter.setClock(Clock.fixed(Instant.now().plus(PostController.POST_WINDOW).plusSeconds(1), ZoneOffset.UTC));
        createPost(session, "1分後");
    }

    @Test
    void loadMorePagesThroughEveryPostWithoutDuplicatesOrGaps() throws Exception {
        String username = newUsername();
        MockHttpSession session = registerAndLogin(username);
        insertPosts(username, PostController.PAGE_SIZE * 2 + 5);

        List<String> ids = allPages("/api/timeline", session);
        assertEquals(allIdsNewestFirst(), ids);
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test
    void postsMadeWhileReadingDoNotShiftTheNextPage() throws Exception {
        String username = newUsername();
        MockHttpSession session = registerAndLogin(username);
        insertPosts(username, PostController.PAGE_SIZE * 2);

        JsonNode first = body(perform(get("/api/timeline").session(session)));
        List<String> firstPage = ids(first);
        assertTrue(first.get("hasMore").asBoolean());

        // 1ページ目を読んでいる間に新しい投稿が増えても、続きは1ページ目の最後の次から
        insertPosts(username, 5);
        JsonNode second = body(perform(get("/api/timeline").session(session).param("before", firstPage.getLast())));
        List<String> secondPage = ids(second);
        List<String> overlap = new ArrayList<>(firstPage);
        overlap.retainAll(secondPage);
        assertTrue(overlap.isEmpty());
        assertEquals(PostController.PAGE_SIZE, secondPage.size());
        assertFalse(second.get("hasMore").asBoolean());
    }

    @Test
    void loadMoreStillWorksWhenTheLastShownPostWasDeleted() throws Exception {
        MockHttpSession session = registerAndLogin();
        String oldest = createPost(session, "投稿 0");
        String middle = createPost(session, "投稿 1");
        createPost(session, "投稿 2");
        mockMvc.perform(delete("/api/posts/" + middle).session(session)).andExpect(status().isNoContent());

        JsonNode page = body(perform(get("/api/timeline").session(session).param("before", middle)));
        assertEquals(List.of(oldest), ids(page));
    }

    @Test
    void hasMoreIsFalseWhenExactlyOnePageIsLeft() throws Exception {
        String username = newUsername();
        MockHttpSession session = registerAndLogin(username);
        insertPosts(username, PostController.PAGE_SIZE);
        mockMvc.perform(get("/api/timeline").session(session))
                .andExpect(jsonPath("$.posts.length()").value(PostController.PAGE_SIZE))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void invalidBeforeIsABadRequest() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(get("/api/timeline").session(session).param("before", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAuthorCanDeleteAPost() throws Exception {
        MockHttpSession author = registerAndLogin();
        String id = createPost(author, "消す投稿");

        // 他人の投稿は存在しない扱い
        mockMvc.perform(delete("/api/posts/" + id).session(registerAndLogin())).andExpect(status().isNotFound());
        assertTrue(postRepository.existsById(Long.valueOf(id)));

        mockMvc.perform(delete("/api/posts/" + id).session(author)).andExpect(status().isNoContent());
        assertFalse(postRepository.existsById(Long.valueOf(id)));
        mockMvc.perform(delete("/api/posts/" + id).session(author)).andExpect(status().isNotFound());
    }

    @Test
    void timelineAndPostingNeedALogin() throws Exception {
        mockMvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/posts"), Map.of("body", "未ログイン"))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void bodiesAreReturnedAsPlainText() throws Exception {
        MockHttpSession session = registerAndLogin();
        String text = "<script>alert(\"x\")</script> & <b>太字</b>";
        createPost(session, text);
        mockMvc.perform(get("/api/timeline").session(session))
                .andExpect(jsonPath("$.posts[0].body").value(text));
    }
}
