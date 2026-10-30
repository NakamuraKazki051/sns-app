package com.snsapp.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** API 経由でユーザー・投稿を作るヘルパー。別ユーザーとして操作したいときは、別のセッションを渡す */
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

    protected MockHttpSession login(String username) throws Exception {
        MvcResult result = perform(json(post("/api/auth/login"),
                Map.of("email", username + "@example.com", "password", PASSWORD)));
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return (MockHttpSession) result.getRequest().getSession();
    }

    protected MockHttpSession registerAndLogin(String username) throws Exception {
        MvcResult result = perform(json(post("/api/auth/register"), registerParams(username)));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return login(username);
    }

    protected MockHttpSession registerAndLogin() throws Exception {
        return registerAndLogin(newUsername());
    }

    /** 投稿して ID を返す */
    protected String createPost(MockHttpSession session, String text) throws Exception {
        MvcResult result = perform(json(post("/api/posts").session(session), Map.of("body", text)));
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return body(result).get("id").asString();
    }

    protected MvcResult createPostResult(MockHttpSession session, Object text) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("body", text);
        return perform(json(post("/api/posts").session(session), params));
    }

    protected static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.get("posts").forEach(p -> ids.add(p.get("id").asString()));
        return ids;
    }

    /** 「もっと見る」を最後までたどって、全ページの投稿 ID を返す */
    protected List<String> allPages(String path, MockHttpSession session) throws Exception {
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
            List<String> pageIds = ids(page);
            assertTrue(pageIds.size() <= 20);
            all.addAll(pageIds);
            if (!page.get("hasMore").asBoolean()) {
                return all;
            }
            before = pageIds.getLast();
        }
    }
}
