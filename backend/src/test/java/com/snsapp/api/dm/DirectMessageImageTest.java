package com.snsapp.api.dm;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.post.ImageStorage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DM の画像と、送ったメッセージの取り消し */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DirectMessageImageTest extends ApiTestSupport {

    @Autowired
    private ImageStorage imageStorage;

    @Autowired
    private DirectMessageRepository messages;

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private static MockMultipartHttpServletRequestBuilder withImage(Cookie from, String to, String text, byte[] data) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/dm/" + to);
        request.file(new MockMultipartFile("image", "photo", "image/png", data));
        if (text != null) {
            request.param("body", text);
        }
        return (MockMultipartHttpServletRequestBuilder) request.cookie(from);
    }

    private JsonNode firstMessage(Cookie me, String partner) throws Exception {
        return body(perform(get("/api/dm/" + partner).cookie(me))).get("messages").get(0);
    }

    @Test
    void imagesCanBeSentAndOnlyTheTwoCanSeeThem() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        Cookie carol = registerAndLogin();
        byte[] data = png(30, 20);

        MvcResult sent = mockMvc.perform(withImage(aliceSession, bob, null, data))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value(""))
                .andExpect(jsonPath("$.image.width").value(30))
                .andExpect(jsonPath("$.image.height").value(20))
                .andReturn();
        String url = body(sent).get("image").get("url").asString();
        assertTrue(url.matches("/api/dm/messages/\\d+/image"));

        JsonNode received = firstMessage(bobSession, alice);
        assertEquals(url, received.get("image").get("url").asString());
        MvcResult image = perform(get(url).cookie(bobSession));
        assertEquals(200, image.getResponse().getStatus());
        assertArrayEquals(data, image.getResponse().getContentAsByteArray());
        assertEquals("image/png", image.getResponse().getContentType());
        mockMvc.perform(get(url).cookie(aliceSession)).andExpect(status().isOk());
        // ほかの人は見られない
        mockMvc.perform(get(url).cookie(carol)).andExpect(status().isNotFound());

        // 本文と一緒にも送れる
        mockMvc.perform(withImage(aliceSession, bob, " 見て ", data))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("見て"));
        // 文字だけのメッセージには画像が無い
        mockMvc.perform(json(post("/api/dm/" + bob), Map.of("body", "テキスト")).cookie(aliceSession))
                .andExpect(jsonPath("$.image").doesNotExist());
    }

    @Test
    void invalidImagesAreRejected() throws Exception {
        Cookie alice = registerAndLogin();
        String bob = newUsername();
        registerAndLogin(bob);
        mockMvc.perform(withImage(alice, bob, null, "not an image".getBytes())).andExpect(status().isBadRequest());
        mockMvc.perform(withImage(alice, bob, null, new byte[0])).andExpect(status().isBadRequest());
    }

    @Test
    void sendersCanTakeBackTheirOwnMessagesWithTheImage() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = registerAndLogin(alice);
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        MvcResult sent = mockMvc.perform(withImage(aliceSession, bob, "消す予定", png(10, 10))).andReturn();
        String id = body(sent).get("id").asString();
        String key = messages.findById(Long.valueOf(id)).orElseThrow().getImage().key();
        assertNotNull(imageStorage.get(key));

        // 受け取った人・ほかの人は消せない
        mockMvc.perform(delete("/api/dm/messages/" + id).cookie(bobSession)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/dm/messages/" + id).cookie(registerAndLogin())).andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/dm/messages/" + id).cookie(aliceSession)).andExpect(status().isNoContent());
        assertEquals(0, body(perform(get("/api/dm/" + alice).cookie(bobSession))).get("messages").size());
        assertNull(imageStorage.get(key));
        mockMvc.perform(delete("/api/dm/messages/" + id).cookie(aliceSession)).andExpect(status().isNotFound());
        // 会話一覧からも消える
        assertEquals(0, body(perform(get("/api/dm").cookie(bobSession))).size());
    }
}
