package com.snsapp.api.user;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.post.ImageStorage;
import com.snsapp.api.post.PostRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** アカウントの削除(F-17) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountDeletionTest extends ApiTestSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ImageStorage imageStorage;

    private ResultActions deleteAccount(Cookie token, String password) throws Exception {
        return mockMvc.perform(json(delete("/api/users/me").cookie(token), Map.of("password", password)));
    }

    @Test
    void deletingRemovesEverythingTheUserMadeAndLogsOutEveryDevice() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie alicePhone = login(aliceName);
        Cookie bob = registerAndLogin(bobName);

        // alice の投稿(画像付き)に bob が反応し、bob の投稿に alice が反応する
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(img, "png", png);
        String alicePost = body(mockMvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("image", "p", "image/png", png.toByteArray()))
                        .param("body", "alice の投稿").cookie(alice))
                .andExpect(status().isCreated()).andReturn()).get("id").asString();
        String imageKey = postRepository.findById(Long.valueOf(alicePost)).orElseThrow().getImage().key();
        assertNotNull(imageStorage.get(imageKey));
        String bobPost = createPost(bob, "bob の投稿");
        mockMvc.perform(json(post("/api/posts/" + alicePost + "/replies").cookie(bob), Map.of("body", "bob から")))
                .andExpect(status().isCreated());
        mockMvc.perform(json(post("/api/posts/" + bobPost + "/replies").cookie(alice), Map.of("body", "alice から")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/posts/" + bobPost + "/like").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + bobPost + "/repost").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + alicePost + "/like").cookie(bob)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + bobName + "/follow").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + aliceName + "/follow").cookie(bob)).andExpect(status().isOk());

        deleteAccount(alice, PASSWORD).andExpect(status().isNoContent());

        // alice のものはすべて消え、bob の投稿の数も戻る
        mockMvc.perform(get("/api/users/" + aliceName).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/" + alicePost).cookie(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/posts/" + bobPost).cookie(bob))
                .andExpect(jsonPath("$.likesCount").value(0))
                .andExpect(jsonPath("$.repliesCount").value(0))
                .andExpect(jsonPath("$.repostsCount").value(0));
        mockMvc.perform(get("/api/users/" + bobName).cookie(bob))
                .andExpect(jsonPath("$.followersCount").value(0))
                .andExpect(jsonPath("$.followingCount").value(0));
        assertNull(imageStorage.get(imageKey));

        // どの端末でも使えず、ログインもできない
        mockMvc.perform(get("/api/auth/me").cookie(alice)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/timeline").cookie(alicePhone)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/auth/login"), Map.of("email", aliceName + "@example.com", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
        // 同じメールアドレス・ユーザー名で登録し直せる
        mockMvc.perform(json(post("/api/auth/register"), registerParams(aliceName))).andExpect(status().isCreated());
    }

    @Test
    void theResponseClearsTheCookie() throws Exception {
        Cookie cleared = deleteAccount(registerAndLogin(), PASSWORD).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SNS_TOKEN");
        assertEquals(0, cleared.getMaxAge());
    }

    @Test
    void aWrongPasswordDeletesNothing() throws Exception {
        String name = newUsername();
        Cookie token = registerAndLogin(name);
        deleteAccount(token, "wrong-password").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("パスワードが正しくありません"));
        mockMvc.perform(get("/api/auth/me").cookie(token)).andExpect(status().isOk());
    }

    @Test
    void tooManyWrongPasswordsAreRefused() throws Exception {
        Cookie token = registerAndLogin();
        for (int i = 0; i < AccountController.DELETE_ATTEMPTS; i++) {
            deleteAccount(token, "wrong-password").andExpect(status().isBadRequest());
        }
        deleteAccount(token, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void needsALoginAndAPassword() throws Exception {
        mockMvc.perform(json(delete("/api/users/me"), Map.of("password", PASSWORD))).andExpect(status().isUnauthorized());
        mockMvc.perform(json(delete("/api/users/me").cookie(registerAndLogin()), Map.of("password", "")))
                .andExpect(status().isBadRequest());
    }
}
