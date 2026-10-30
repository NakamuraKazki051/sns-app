package com.snsapp.api.user;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SearchAndBioTest extends ApiTestSupport {

    private void register(String username, String displayName) throws Exception {
        Map<String, Object> params = registerParams(username);
        params.put("displayName", displayName);
        assertEquals(201, perform(json(post("/api/auth/register"), params)).getResponse().getStatus());
    }

    private List<String> search(MockHttpSession session, String q) throws Exception {
        List<String> all = new ArrayList<>();
        String cursor = null;
        while (true) {
            MockHttpServletRequestBuilder request = get("/api/users").param("q", q).session(session);
            if (cursor != null) {
                request.param("after", cursor);
            }
            JsonNode page = body(perform(request));
            page.get("users").forEach(u -> all.add(u.get("username").asString()));
            if (page.get("nextCursor").isNull()) {
                return all;
            }
            cursor = page.get("nextCursor").asString();
        }
    }

    @Test
    void searchMatchesPartOfTheUsernameOrDisplayNameIgnoringCase() throws Exception {
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        register("a" + tag + "x", "誰か");
        register("b_other", "表示名に" + tag + "を含む");
        register("c" + tag.toUpperCase(), "大文字");
        MockHttpSession session = registerAndLogin();

        // ユーザー名順。b_other は表示名で当たる
        assertEquals(List.of("a" + tag + "x", "b_other", "c" + tag.toUpperCase()), search(session, tag));
        assertEquals(List.of("b_other"), search(session, "表示名に" + tag));
    }

    @Test
    void searchResultsArePagedWithoutDuplicatesOrGaps() throws Exception {
        String tag = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < UserController.PAGE_SIZE + 3; i++) {
            String name = tag + String.format("%02d", i);
            register(name, "ページ送り");
            expected.add(name);
        }
        assertEquals(expected, search(registerAndLogin(), tag));
    }

    @Test
    void likeWildcardsAreSearchedLiterally() throws Exception {
        MockHttpSession session = registerAndLogin();
        // % や _ をそのまま渡しても、全員に当たったりしない
        assertEquals(0, body(perform(get("/api/users").param("q", "%%%").session(session))).get("users").size());
        String tag = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        register("u" + tag, "アンダースコア_" + tag);
        register("v" + tag, "アンダースコアX" + tag);
        assertEquals(List.of("u" + tag), search(session, "_" + tag));
    }

    @Test
    void emptyOrTooLongQueries() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(get("/api/users").param("q", "  ").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        mockMvc.perform(get("/api/users").param("q", "x".repeat(51)).session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users").param("q", "x")).andExpect(status().isUnauthorized());
    }

    @Test
    void bioCanBeSetClearedAndIsShownOnTheUserPage() throws Exception {
        String name = newUsername();
        MockHttpSession session = registerAndLogin(name);

        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("bio", "  よろしく\nお願いします  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("よろしく\nお願いします"))
                .andExpect(jsonPath("$.displayName").value("表示名 " + name));
        mockMvc.perform(get("/api/users/" + name).session(registerAndLogin()))
                .andExpect(jsonPath("$.bio").value("よろしく\nお願いします"));

        // 表示名だけ送ると、自己紹介はそのまま
        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("displayName", "新しい名前")))
                .andExpect(jsonPath("$.bio").value("よろしく\nお願いします"));

        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("bio", "")))
                .andExpect(jsonPath("$.bio").isEmpty());
    }

    @Test
    void bioIsLimitedTo160VisibleCharacters() throws Exception {
        MockHttpSession session = registerAndLogin();
        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("bio", "👍🏽".repeat(160))))
                .andExpect(status().isOk());
        mockMvc.perform(json(patch("/api/me/profile").session(session), Map.of("bio", "あ".repeat(161))))
                .andExpect(status().isBadRequest());
        Map<String, Object> nothing = new HashMap<>();
        mockMvc.perform(json(patch("/api/me/profile").session(session), nothing)).andExpect(status().isOk());
    }
}
