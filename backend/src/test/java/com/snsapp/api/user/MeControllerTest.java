package com.snsapp.api.user;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeControllerTest extends ApiTestSupport {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5, 6};

    /** multipart() は POST になるので、PUT に変える */
    private static MockMultipartHttpServletRequestBuilder putIcon(MockHttpSession session, String contentType,
                                                                  byte[] data) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/me/icon");
        if (data != null) {
            request.file(new MockMultipartFile("icon", "icon", contentType, data));
        }
        request.session(session).with(r -> {
            r.setMethod("PUT");
            return r;
        });
        return request;
    }

    @Test
    void displayNameCanBeChangedAndIsStripped() throws Exception {
        String alice = newUsername();
        MockHttpSession session = registerAndLogin(alice);
        createPost(session, "こんにちは");

        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("displayName", "  アリス  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("アリス"));

        mockMvc.perform(get("/api/users/" + alice).session(session))
                .andExpect(jsonPath("$.displayName").value("アリス"));
        mockMvc.perform(get("/api/timeline").session(session))
                .andExpect(jsonPath("$.posts[0].user.displayName").value("アリス"));
    }

    @Test
    void blankOrTooLongDisplayNamesAreRejected() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("displayName", "   ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("displayName", "あ".repeat(51))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("displayName", "あ".repeat(50))))
                .andExpect(status().isOk());
    }

    @Test
    void iconCanBeSetReplacedAndRemoved() throws Exception {
        String alice = newUsername();
        MockHttpSession session = registerAndLogin(alice);
        MockHttpSession bob = registerAndLogin();
        createPost(session, "アイコンつき");

        MvcResult first = mockMvc.perform(putIcon(session, "image/png", PNG)).andReturn();
        assertEquals(200, first.getResponse().getStatus(), first.getResponse().getContentAsString());
        String firstVersion = body(first).get("iconUpdatedAt").asString();

        // 他の人からも見える。投稿とプロフィールの応答には更新時刻だけが入る
        mockMvc.perform(get("/api/users/" + alice + "/icon").session(bob))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", startsWith("max-age=")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/users/" + alice).session(bob))
                .andExpect(jsonPath("$.iconUpdatedAt").value(firstVersion));
        mockMvc.perform(get("/api/timeline").session(bob))
                .andExpect(jsonPath("$.posts[0].user.iconUpdatedAt").value(firstVersion));

        Thread.sleep(5);
        MvcResult second = mockMvc.perform(putIcon(session, "image/jpeg", JPEG)).andReturn();
        assertEquals(200, second.getResponse().getStatus());
        assertNotEquals(firstVersion, body(second).get("iconUpdatedAt").asString(), "変更したら URL の ?v= も変わる");
        MvcResult image = perform(get("/api/users/" + alice + "/icon").session(bob));
        assertEquals("image/jpeg", image.getResponse().getContentType());
        assertArrayEquals(JPEG, image.getResponse().getContentAsByteArray());

        mockMvc.perform(delete("/api/me/icon").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iconUpdatedAt").isEmpty());
        mockMvc.perform(get("/api/users/" + alice + "/icon").session(bob)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.iconUpdatedAt").isEmpty());
    }

    @Test
    void nonImageFilesAreRejectedEvenWithAnImageContentType() throws Exception {
        MockHttpSession session = registerAndLogin();
        byte[] html = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(putIcon(session, "image/png", html)).andExpect(status().isBadRequest());
        mockMvc.perform(putIcon(session, "image/gif", "GIF89a....".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(putIcon(session, "image/png", new byte[0])).andExpect(status().isBadRequest());
    }

    @Test
    void iconsOverOneMegabyteAreRejected() throws Exception {
        MockHttpSession session = registerAndLogin();
        byte[] tooBig = Arrays.copyOf(PNG, UserIcon.MAX_BYTES + 1);
        mockMvc.perform(putIcon(session, "image/png", tooBig)).andExpect(status().isContentTooLarge());

        byte[] justFits = Arrays.copyOf(PNG, UserIcon.MAX_BYTES);
        mockMvc.perform(putIcon(session, "image/png", justFits)).andExpect(status().isOk());
    }

    @Test
    void missingIconPartIsABadRequest() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(putIcon(session, null, null)).andExpect(status().isBadRequest());
    }

    @Test
    void settingsAndIconsRequireLogin() throws Exception {
        String alice = newUsername();
        registerAndLogin(alice);
        mockMvc.perform(json(patch("/api/me/profile"), Map.of("displayName", "x"))).andExpect(status().isUnauthorized());
        mockMvc.perform(putIcon(new MockHttpSession(), "image/png", PNG)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/me/icon")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/" + alice + "/icon")).andExpect(status().isUnauthorized());
    }
}
