package com.snsapp.api.repost;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepostTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private RepostRepository repostRepository;

    @Test
    void repostAndUndoUpdateTheCountAndMyState() throws Exception {
        MockHttpSession alice = registerAndLogin();
        MockHttpSession bob = registerAndLogin();
        MockHttpSession carol = registerAndLogin();
        String postId = createPost(alice, "広めてね");

        mockMvc.perform(post("/api/posts/" + postId + "/repost").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reposted").value(true))
                .andExpect(jsonPath("$.repostsCount").value(1));
        // もう一度押しても1回分のまま
        mockMvc.perform(post("/api/posts/" + postId + "/repost").session(bob))
                .andExpect(jsonPath("$.repostsCount").value(1));
        mockMvc.perform(post("/api/posts/" + postId + "/repost").session(carol))
                .andExpect(jsonPath("$.repostsCount").value(2));

        mockMvc.perform(get("/api/posts/" + postId).session(bob))
                .andExpect(jsonPath("$.repostsCount").value(2))
                .andExpect(jsonPath("$.repostedByMe").value(true))
                .andExpect(jsonPath("$.repostedBy").isEmpty());

        mockMvc.perform(delete("/api/posts/" + postId + "/repost").session(bob))
                .andExpect(jsonPath("$.reposted").value(false))
                .andExpect(jsonPath("$.repostsCount").value(1));
        mockMvc.perform(delete("/api/posts/" + postId + "/repost").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repostsCount").value(1));
        mockMvc.perform(get("/api/posts/" + postId).session(bob))
                .andExpect(jsonPath("$.repostedByMe").value(false));
    }

    @Test
    void cannotRepostOwnOrUnknownPostsOrWithoutLogin() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String postId = createPost(alice, "自分の投稿");
        mockMvc.perform(post("/api/posts/" + postId + "/repost").session(alice)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/posts/999999999/repost").session(alice)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/999999999/repost").session(alice)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/" + postId + "/repost")).andExpect(status().isUnauthorized());
    }

    @Test
    void followersSeeRepostsOnHomeAndTheReposterPageLabeledWithWhoReposted() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String bobName = newUsername();
        MockHttpSession bob = registerAndLogin(bobName);
        MockHttpSession carol = registerAndLogin();
        mockMvc.perform(post("/api/users/" + bobName + "/follow").session(alice));

        String carols = createPost(carol, "キャロルの投稿");
        String alices = createPost(alice, "アリスの投稿");
        mockMvc.perform(post("/api/posts/" + carols + "/repost").session(bob));

        // フォローしていないキャロルの投稿が、ボブのリポストとして(リポストした時刻の位置に)出る
        mockMvc.perform(get("/api/timeline/home").session(alice))
                .andExpect(jsonPath("$.posts.length()").value(2))
                .andExpect(jsonPath("$.posts[0].id").value(carols))
                .andExpect(jsonPath("$.posts[0].repostedBy.username").value(bobName))
                .andExpect(jsonPath("$.posts[0].repostedBy.email").doesNotExist())
                .andExpect(jsonPath("$.posts[0].repostsCount").value(1))
                .andExpect(jsonPath("$.posts[1].id").value(alices))
                .andExpect(jsonPath("$.posts[1].repostedBy").isEmpty());

        mockMvc.perform(get("/api/users/" + bobName + "/posts").session(carol))
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(carols))
                .andExpect(jsonPath("$.posts[0].repostedBy.username").value(bobName));

        // 全体タイムラインは元の投稿だけ(同じ投稿を2回出さない)
        JsonNode all = body(perform(get("/api/timeline").session(alice)));
        List<JsonNode> carolsInAll = new ArrayList<>();
        all.get("posts").forEach(p -> {
            if (p.get("id").asString().equals(carols)) carolsInAll.add(p);
        });
        assertEquals(1, carolsInAll.size());
        assertTrue(carolsInAll.getFirst().get("repostedBy").isNull());

        // 取り消すとタイムラインからも消える
        mockMvc.perform(delete("/api/posts/" + carols + "/repost").session(bob));
        assertEquals(List.of(alices), allPages("/api/timeline/home", alice));
    }

    @Test
    void deletingThePostAlsoDeletesItsReposts() throws Exception {
        String bobName = newUsername();
        MockHttpSession bob = registerAndLogin(bobName);
        MockHttpSession carol = registerAndLogin();
        String carols = createPost(carol, "消える投稿");
        mockMvc.perform(post("/api/posts/" + carols + "/repost").session(bob));

        mockMvc.perform(delete("/api/posts/" + carols).session(carol)).andExpect(status().isNoContent());
        assertEquals(List.of(), allPages("/api/users/" + bobName + "/posts", bob));
        assertEquals(0, repostRepository.countByPostId(Long.valueOf(carols)));
    }

    @Test
    void homeMixingPostsAndRepostsIsPagedWithoutDuplicatesOrGaps() throws Exception {
        String aliceName = newUsername();
        MockHttpSession alice = registerAndLogin(aliceName);
        String bobName = newUsername();
        registerAndLogin(bobName);
        String carolName = newUsername();
        registerAndLogin(carolName);
        mockMvc.perform(post("/api/users/" + bobName + "/follow").session(alice));
        User aliceUser = userRepository.findByUsername(aliceName).orElseThrow();
        User bobUser = userRepository.findByUsername(bobName).orElseThrow();
        User carolUser = userRepository.findByUsername(carolName).orElseThrow();

        // 古いキャロルの投稿を、ボブが投稿の合間にリポストしていく。連投制限にかからないよう DB に直接入れる
        List<Post> carols = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            carols.add(postRepository.save(new Post(carolUser, "キャロル " + i)));
        }
        List<String> expected = new ArrayList<>();
        int reposted = 0;
        for (int i = 0; i < PostController.PAGE_SIZE * 2 + 5; i++) {
            Thread.sleep(2); // 投稿とリポストの時刻の順番をはっきりさせる
            if (i % 3 == 0) {
                Post target = carols.get(reposted++);
                repostRepository.save(new Repost(bobUser, target));
                expected.add(0, "r" + target.getId() + "@" + bobName);
            } else {
                Post post = postRepository.save(new Post(i % 2 == 0 ? aliceUser : bobUser, "投稿 " + i));
                expected.add(0, "p" + post.getId());
            }
        }
        assertEquals(expected, allEntries("/api/timeline/home", alice));
    }

    @Test
    void malformedCursorIsABadRequest() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(get("/api/timeline/home").session(session).param("before", "abc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/timeline/home").session(session).param("before", "1:2:3"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/timeline/home").session(session).param("before", "5:"))
                .andExpect(status().isOk());
    }

    /** 全ページの行を「p投稿ID」(元の投稿)か「r投稿ID@リポストした人」(リポスト)にして返す */
    private List<String> allEntries(String path, MockHttpSession session) throws Exception {
        List<String> all = new ArrayList<>();
        String before = null;
        while (true) {
            MockHttpServletRequestBuilder request = get(path).session(session);
            if (before != null) {
                request.param("before", before);
            }
            MvcResult result = perform(request);
            assertEquals(200, result.getResponse().getStatus());
            JsonNode page = body(result);
            assertTrue(page.get("posts").size() <= PostController.PAGE_SIZE);
            page.get("posts").forEach(p -> all.add(p.get("repostedBy").isNull()
                    ? "p" + p.get("id").asString()
                    : "r" + p.get("id").asString() + "@" + p.get("repostedBy").get("username").asString()));
            if (!page.get("hasMore").asBoolean()) {
                return all;
            }
            before = page.get("nextCursor").asString();
        }
    }
}
