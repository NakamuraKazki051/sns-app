package com.snsapp.api.post;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 投稿の編集(F-19) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostEditTest extends ApiTestSupport {

    private ResultActions edit(Cookie token, String id, Object body) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("body", body);
        return mockMvc.perform(json(patch("/api/posts/" + id).cookie(token), params));
    }

    @Test
    void theOwnerCanChangeTheTextAndItIsMarkedAsEdited() throws Exception {
        Cookie alice = registerAndLogin();
        String id = createPost(alice, "誤字がある投搞");
        mockMvc.perform(get("/api/posts/" + id).cookie(alice)).andExpect(jsonPath("$.editedAt").isEmpty());

        edit(alice, id, "  誤字を直した投稿  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("誤字を直した投稿"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
        mockMvc.perform(get("/api/posts/" + id).cookie(registerAndLogin()))
                .andExpect(jsonPath("$.body").value("誤字を直した投稿"))
                .andExpect(jsonPath("$.editedAt").isNotEmpty());
    }

    @Test
    void sendingTheSameTextDoesNotMarkItAsEdited() throws Exception {
        Cookie alice = registerAndLogin();
        String id = createPost(alice, "そのまま");
        edit(alice, id, "そのまま").andExpect(status().isOk()).andExpect(jsonPath("$.editedAt").isEmpty());
    }

    @Test
    void othersPostsLookMissing() throws Exception {
        String id = createPost(registerAndLogin(), "alice の投稿");
        edit(registerAndLogin(), id, "書き換え").andExpect(status().isNotFound());
        edit(registerAndLogin(), "999999999", "書き換え").andExpect(status().isNotFound());
    }

    @Test
    void theTextFollowsTheSameRulesAsAPost() throws Exception {
        Cookie alice = registerAndLogin();
        String id = createPost(alice, "元の本文");
        edit(alice, id, "   ").andExpect(status().isBadRequest());
        edit(alice, id, null).andExpect(status().isBadRequest());
        edit(alice, id, "あ".repeat(141)).andExpect(status().isBadRequest());
        edit(alice, id, "あ".repeat(140)).andExpect(status().isOk());
    }

    @Test
    void anImagePostCanLoseItsTextButKeepsTheImage() throws Exception {
        Cookie alice = registerAndLogin();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", png);
        String id = body(mockMvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("image", "p", "image/png", png.toByteArray()))
                        .param("body", "写真に一言").cookie(alice))
                .andExpect(status().isCreated()).andReturn()).get("id").asString();

        edit(alice, id, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.image.width").value(8));
    }

    @Test
    void needsALogin() throws Exception {
        String id = createPost(registerAndLogin(), "本文");
        mockMvc.perform(json(patch("/api/posts/" + id), Map.of("body", "x"))).andExpect(status().isUnauthorized());
    }
}
