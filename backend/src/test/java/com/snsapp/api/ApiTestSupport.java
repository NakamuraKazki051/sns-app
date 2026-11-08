package com.snsapp.api;

import com.snsapp.api.auth.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** API 経由でユーザー・投稿を作るヘルパー。別ユーザーとして操作したいときは、その人のログインの Cookie(JWT)を渡す */
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "correcthorsebattery";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static String newUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected static Map<String, Object> registerParams(String username) {
        Map<String, Object> params = new HashMap<>();
        params.put("email", username + "@example.com");
        params.put("password", PASSWORD);
        params.put("username", username);
        params.put("displayName", "表示名 " + username);
        return params;
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    protected MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected Cookie login(String username) throws Exception {
        MvcResult result = perform(json(post("/api/auth/login"),
                Map.of("email", username + "@example.com", "password", PASSWORD)));
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        Cookie token = result.getResponse().getCookie(CurrentUser.COOKIE_NAME);
        assertNotNull(token, "ログインの応答に Cookie が無い");
        return token;
    }

    protected Cookie registerAndLogin(String username) throws Exception {
        MvcResult result = perform(json(post("/api/auth/register"), registerParams(username)));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return login(username);
    }

    protected Cookie registerAndLogin() throws Exception {
        return registerAndLogin(newUsername());
    }

    /** 投稿して ID を返す */
    protected String createPost(Cookie session, String text) throws Exception {
        MvcResult result = perform(json(post("/api/posts").cookie(session), Map.of("body", text)));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return body(result).get("id").asString();
    }

    protected MvcResult createPostResult(Cookie session, Object text) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("body", text);
        return perform(json(post("/api/posts").cookie(session), params));
    }

    protected static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("posts").forEach(p -> ids.add(p.get("id").asString()));
        return ids;
    }

    /** 「もっと見る」を最後までたどって(前のページの nextCursor を before に渡す)、全ページの投稿 ID を返す */
    protected List<String> allPages(String path, Cookie session) throws Exception {
        List<String> all = new ArrayList<>();
        String before = null;
        while (true) {
            MockHttpServletRequestBuilder request = get(path).cookie(session);
            if (before != null) {
                request.param("before", before);
            }
            MvcResult result = perform(request);
            assertEquals(200, result.getResponse().getStatus());
            JsonNode page = body(result);
            List<String> pageIds = ids(page);
            assertTrue(pageIds.size() <= 20);
            all.addAll(pageIds);
            if (!page.get("hasMore").asBoolean()) {
                assertTrue(page.get("nextCursor").isNull());
                return all;
            }
            before = page.get("nextCursor").asString();
        }
    }
}
