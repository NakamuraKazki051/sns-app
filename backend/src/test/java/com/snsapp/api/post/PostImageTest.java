package com.snsapp.api.post;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 画像付きの投稿(F-14) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostImageTest extends ApiTestSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ImageStorage imageStorage;

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    private static MockMultipartHttpServletRequestBuilder postWithImage(MockHttpSession session, String text,
                                                                        byte[] data) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/posts");
        request.file(new MockMultipartFile("image", "photo", "image/png", data));
        if (text != null) {
            request.param("body", text);
        }
        return (MockMultipartHttpServletRequestBuilder) request.session(session);
    }

    @Test
    void postWithImageReturnsItsSizeAndServesTheImage() throws Exception {
        MockHttpSession session = registerAndLogin();
        byte[] png = image("png", 40, 30);

        MvcResult result = mockMvc.perform(postWithImage(session, "海に行った", png))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("海に行った"))
                .andExpect(jsonPath("$.image.width").value(40))
                .andExpect(jsonPath("$.image.height").value(30))
                .andReturn();
        String id = body(result).get("id").asString();
        assertEquals("/api/posts/" + id + "/image", body(result).get("image").get("url").asString());

        mockMvc.perform(get("/api/posts/" + id + "/image").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(png));

        // タイムラインにも画像の情報が出る
        JsonNode first = body(perform(get("/api/timeline").session(session))).get("posts").get(0);
        assertEquals(id, first.get("id").asString());
        assertEquals(40, first.get("image").get("width").asInt());
    }

    @Test
    void imageOnlyPostIsAllowedAndJpegWorks() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(postWithImage(session, null, image("jpg", 8, 16)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.image.height").value(16));
        mockMvc.perform(postWithImage(session, "   ", image("jpg", 8, 16)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(""));
    }

    @Test
    void postWithoutImageHasNullImage() throws Exception {
        MockHttpSession session = registerAndLogin();
        String id = createPost(session, "文字だけ");
        JsonNode post = body(perform(get("/api/posts/" + id).session(session)));
        assertTrue(post.get("image").isNull());
        mockMvc.perform(get("/api/posts/" + id + "/image").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsFilesThatAreNotJpegOrPng() throws Exception {
        MockHttpSession session = registerAndLogin();
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(postWithImage(session, "x", html)).andExpect(status().isBadRequest());
        mockMvc.perform(postWithImage(session, "x", image("gif", 4, 4))).andExpect(status().isBadRequest());
        // 先頭だけ PNG に見せかけた壊れたファイル
        byte[] fakePng = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        mockMvc.perform(postWithImage(session, "x", fakePng)).andExpect(status().isBadRequest());
        mockMvc.perform(postWithImage(session, "x", new byte[0])).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooLargeFilesAndTooLongText() throws Exception {
        MockHttpSession session = registerAndLogin();
        byte[] tooBig = Arrays.copyOf(image("png", 4, 4), PostImage.MAX_BYTES + 1);
        mockMvc.perform(postWithImage(session, "x", tooBig)).andExpect(status().isContentTooLarge());
        mockMvc.perform(postWithImage(session, "あ".repeat(141), image("png", 4, 4)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsHugeDimensions() throws Exception {
        MockHttpSession session = registerAndLogin();
        // 1色だけなので、ファイルは小さくても縦が上限を超える
        byte[] tall = image("png", 1, PostImage.MAX_SIDE + 1);
        mockMvc.perform(postWithImage(session, "x", tall)).andExpect(status().isBadRequest());
    }

    @Test
    void deletingThePostDeletesTheImage() throws Exception {
        MockHttpSession session = registerAndLogin();
        MvcResult result = mockMvc.perform(postWithImage(session, "消す", image("png", 4, 4))).andReturn();
        Long id = Long.valueOf(body(result).get("id").asString());
        String key = postRepository.findById(id).orElseThrow().getImage().key();
        assertNotNull(imageStorage.get(key));

        mockMvc.perform(delete("/api/posts/" + id).session(session)).andExpect(status().isNoContent());
        assertNull(imageStorage.get(key));
    }

    @Test
    void othersCannotDeleteTheImage() throws Exception {
        MockHttpSession alice = registerAndLogin();
        MockHttpSession bob = registerAndLogin();
        byte[] png = image("png", 4, 4);
        MvcResult result = mockMvc.perform(postWithImage(alice, "消させない", png)).andReturn();
        String id = body(result).get("id").asString();

        mockMvc.perform(delete("/api/posts/" + id).session(bob)).andExpect(status().isNotFound());
        // 他の人も見られる
        MvcResult image = perform(get("/api/posts/" + id + "/image").session(bob));
        assertArrayEquals(png, image.getResponse().getContentAsByteArray());
    }

    @Test
    void imageNeedsLogin() throws Exception {
        MockHttpSession session = registerAndLogin();
        MvcResult result = mockMvc.perform(postWithImage(session, "x", image("png", 4, 4))).andReturn();
        String id = body(result).get("id").asString();
        mockMvc.perform(get("/api/posts/" + id + "/image")).andExpect(status().isUnauthorized());
    }
}
