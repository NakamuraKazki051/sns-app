package com.snsapp.api.like;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LikeTest extends ApiTestSupport {

    @Autowired
    private PostLikeRepository likeRepository;

    @Test
    @Transactional
    void likeAndUnlikeUpdateTheCountAndMyState() throws Exception {
        MockHttpSession alice = registerAndLogin();
        MockHttpSession bob = registerAndLogin();
        String postId = createPost(alice, "いいねしてね");

        mockMvc.perform(post("/api/posts/" + postId + "/like").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likesCount").value(1));
        // もう一度押しても1回分のまま
        mockMvc.perform(post("/api/posts/" + postId + "/like").session(bob))
                .andExpect(jsonPath("$.likesCount").value(1));
        mockMvc.perform(post("/api/posts/" + postId + "/like").session(alice))
                .andExpect(jsonPath("$.likesCount").value(2));

        mockMvc.perform(get("/api/timeline").session(bob))
                .andExpect(jsonPath("$.posts[0].likesCount").value(2))
                .andExpect(jsonPath("$.posts[0].likedByMe").value(true));

        mockMvc.perform(delete("/api/posts/" + postId + "/like").session(bob))
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likesCount").value(1));
        mockMvc.perform(delete("/api/posts/" + postId + "/like").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likesCount").value(1));
        mockMvc.perform(get("/api/posts/" + postId).session(bob))
                .andExpect(jsonPath("$.likedByMe").value(false))
                .andExpect(jsonPath("$.likesCount").value(1));
    }

    @Test
    @Transactional
    void likingAMissingPostIsNotFoundAndLoginIsRequired() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(post("/api/posts/999999/like").session(session)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/posts/999999/like").session(session)).andExpect(status().isNotFound());
        String postId = createPost(session, "投稿");
        mockMvc.perform(post("/api/posts/" + postId + "/like")).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void deletingAPostAlsoDeletesItsLikes() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String postId = createPost(alice, "消す投稿");
        mockMvc.perform(post("/api/posts/" + postId + "/like").session(alice));
        long before = likeRepository.count();
        mockMvc.perform(delete("/api/posts/" + postId).session(alice)).andExpect(status().isNoContent());
        assertEquals(before - 1, likeRepository.count());
    }

    /** 連打(同時に何回も届く)しても、いいねは1件だけ。トランザクションをまたぐので、最後に投稿ごと消して片付ける */
    @Test
    void rapidDoubleClicksStillCountOnce() throws Exception {
        MockHttpSession alice = registerAndLogin();
        String postId = createPost(alice, "連打される投稿");
        int clicks = 8;
        ExecutorService pool = Executors.newFixedThreadPool(clicks);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < clicks; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return perform(post("/api/posts/" + postId + "/like").session(alice)).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(200, result.get());
            }
            mockMvc.perform(get("/api/posts/" + postId).session(alice))
                    .andExpect(jsonPath("$.likesCount").value(1));
        } finally {
            pool.shutdown();
            mockMvc.perform(delete("/api/posts/" + postId).session(alice));
        }
    }
}
