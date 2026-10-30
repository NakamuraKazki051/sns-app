package com.snsapp.api.follow;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.post.Post;
import com.snsapp.api.post.PostController;
import com.snsapp.api.post.PostRepository;
import com.snsapp.api.user.User;
import com.snsapp.api.user.UserController;
import com.snsapp.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FollowTest extends ApiTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Test
    void followAndUnfollowUpdateCountsAndState() throws Exception {
        String aliceName = newUsername();
        MockHttpSession alice = registerAndLogin(aliceName);
        String bobName = newUsername();
        MockHttpSession bob = registerAndLogin(bobName);

        mockMvc.perform(post("/api/users/" + bobName + "/follow").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.followersCount").value(1));
        // 2回押しても1回分
        mockMvc.perform(post("/api/users/" + bobName + "/follow").session(alice))
                .andExpect(jsonPath("$.followersCount").value(1));

        mockMvc.perform(get("/api/users/" + bobName).session(alice))
                .andExpect(jsonPath("$.followersCount").value(1))
                .andExpect(jsonPath("$.followingCount").value(0))
                .andExpect(jsonPath("$.followedByMe").value(true));
        mockMvc.perform(get("/api/users/" + aliceName).session(bob))
                .andExpect(jsonPath("$.followingCount").value(1))
                .andExpect(jsonPath("$.followedByMe").value(false));

        mockMvc.perform(delete("/api/users/" + bobName + "/follow").session(alice))
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followersCount").value(0));
        mockMvc.perform(delete("/api/users/" + bobName + "/follow").session(alice)).andExpect(status().isOk());
    }

    @Test
    void cannotFollowYourselfOrUnknownUsers() throws Exception {
        String name = newUsername();
        MockHttpSession session = registerAndLogin(name);
        mockMvc.perform(post("/api/users/" + name + "/follow").session(session)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/" + name.toUpperCase() + "/follow").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/nobody_here_x/follow").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/" + name + "/follow")).andExpect(status().isUnauthorized());
    }

    @Test
    void followingAndFollowerListsArePagedNewestFirst() throws Exception {
        String starName = newUsername();
        MockHttpSession star = registerAndLogin(starName);
        List<String> fans = new ArrayList<>();
        for (int i = 0; i < UserController.PAGE_SIZE + 2; i++) {
            String fan = newUsername();
            mockMvc.perform(post("/api/users/" + starName + "/follow").session(registerAndLogin(fan)));
            fans.add(0, fan);
        }
        assertEquals(fans, allUsers("/api/users/" + starName + "/followers", star));

        String fanName = fans.getFirst();
        mockMvc.perform(get("/api/users/" + fanName + "/following").session(star))
                .andExpect(jsonPath("$.users[0].username").value(starName))
                .andExpect(jsonPath("$.users[0].email").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void homeTimelineShowsOnlyMeAndPeopleIFollow() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String bobName = newUsername();
        MockHttpSession bob = registerAndLogin(bobName);
        MockHttpSession carol = registerAndLogin();

        String mine = createPost(alice, "自分の投稿");
        String bobs = createPost(bob, "ボブの投稿");
        createPost(carol, "キャロルの投稿");
        mockMvc.perform(post("/api/users/" + bobName + "/follow").session(alice));

        assertEquals(List.of(bobs, mine), allPages("/api/timeline/home", alice));

        mockMvc.perform(delete("/api/users/" + bobName + "/follow").session(alice));
        assertEquals(List.of(mine), allPages("/api/timeline/home", alice));
    }

    @Test
    void homeTimelineIsPagedWithoutDuplicatesOrGaps() throws Exception {
        String aliceName = newUsername();
        MockHttpSession alice = registerAndLogin(aliceName);
        String bobName = newUsername();
        registerAndLogin(bobName);
        mockMvc.perform(post("/api/users/" + bobName + "/follow").session(alice));
        User aliceUser = userRepository.findByUsername(aliceName).orElseThrow();
        User bobUser = userRepository.findByUsername(bobName).orElseThrow();
        // 連投制限(1分10件)にかからないよう、DB に直接入れる
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < PostController.PAGE_SIZE + 5; i++) {
            Post post = postRepository.save(new Post(i % 2 == 0 ? aliceUser : bobUser, "投稿 " + i));
            expected.add(0, post.getId().toString());
        }
        assertEquals(expected, allPages("/api/timeline/home", alice));
    }

    private List<String> allUsers(String path, MockHttpSession session) throws Exception {
        List<String> all = new ArrayList<>();
        String cursor = null;
        while (true) {
            MockHttpServletRequestBuilder request = get(path).session(session);
            if (cursor != null) {
                request.param("before", cursor);
            }
            JsonNode page = body(perform(request));
            page.get("users").forEach(u -> all.add(u.get("username").asString()));
            if (page.get("nextCursor").isNull()) {
                return all;
            }
            cursor = page.get("nextCursor").asString();
        }
    }
}
