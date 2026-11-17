package com.snsapp.api.report;

import com.snsapp.api.ApiTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 通報と、管理者の対応(テストでは report_admin が管理者) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportTest extends ApiTestSupport {

    private static Map<String, Object> report(String key, String value, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put(key, value);
        body.put("reason", reason);
        return body;
    }

    private JsonNode openReports(Cookie admin) throws Exception {
        return body(perform(get("/api/admin/reports").cookie(admin)));
    }

    @Test
    void usersAndPostsCanBeReportedAndAdminsSeeThem() throws Exception {
        Cookie admin = registerAndLogin("report_admin");
        Cookie alice = registerAndLogin();
        String bob = newUsername();
        Cookie bobSession = registerAndLogin(bob);
        String postId = createPost(bobSession, "宣伝です");

        Map<String, Object> userReport = report("username", bob, "IMPERSONATION");
        userReport.put("detail", "  有名人のふりをしています  ");
        mockMvc.perform(json(post("/api/reports"), userReport).cookie(alice)).andExpect(status().isCreated());
        mockMvc.perform(json(post("/api/reports"), report("postId", postId, "SPAM")).cookie(alice))
                .andExpect(status().isCreated());

        JsonNode list = openReports(admin);
        assertEquals(2, list.size());
        // 古い順
        assertEquals("IMPERSONATION", list.get(0).get("reason").asString());
        assertEquals("有名人のふりをしています", list.get(0).get("detail").asString());
        assertEquals(bob, list.get(0).get("target").get("username").asString());
        assertTrue(list.get(0).get("post").isNull());
        assertEquals("SPAM", list.get(1).get("reason").asString());
        assertEquals(postId, list.get(1).get("post").get("id").asString());
        assertEquals("宣伝です", list.get(1).get("post").get("body").asString());
        assertEquals(bob, list.get(1).get("target").get("username").asString());

        mockMvc.perform(get("/api/admin/status").cookie(admin))
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.openReports").value(2));
    }

    @Test
    void theSameOpenReportIsNotCountedTwice() throws Exception {
        Cookie admin = registerAndLogin("report_admin");
        Cookie alice = registerAndLogin();
        String bob = newUsername();
        registerAndLogin(bob);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(json(post("/api/reports"), report("username", bob, "SPAM")).cookie(alice))
                    .andExpect(status().isCreated());
        }
        assertEquals(1, openReports(admin).size());
    }

    @Test
    void invalidReportsAreRejected() throws Exception {
        String alice = newUsername();
        Cookie session = registerAndLogin(alice);
        // 自分
        mockMvc.perform(json(post("/api/reports"), report("username", alice, "SPAM")).cookie(session))
                .andExpect(status().isBadRequest());
        // 理由が無い・知らない理由
        mockMvc.perform(json(post("/api/reports"), Map.of("username", newUsername())).cookie(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/reports"), report("username", "someone", "ANGRY")).cookie(session))
                .andExpect(status().isBadRequest());
        // 相手がいない
        mockMvc.perform(json(post("/api/reports"), report("username", "nobody_here", "SPAM")).cookie(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/reports"), report("postId", "999999999", "SPAM")).cookie(session))
                .andExpect(status().isNotFound());
        // 説明が長すぎる
        Map<String, Object> tooLong = report("username", registerUserOnly(), "OTHER");
        tooLong.put("detail", "あ".repeat(501));
        mockMvc.perform(json(post("/api/reports"), tooLong).cookie(session)).andExpect(status().isBadRequest());
    }

    @Test
    void reportsPerHourAreLimited() throws Exception {
        Cookie alice = registerAndLogin();
        for (int i = 0; i < ReportController.REPORTS_PER_HOUR; i++) {
            mockMvc.perform(json(post("/api/reports"), report("username", registerUserOnly(), "SPAM")).cookie(alice))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(json(post("/api/reports"), report("username", registerUserOnly(), "SPAM")).cookie(alice))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void onlyAdminsCanSeeAndResolveReports() throws Exception {
        Cookie alice = registerAndLogin();
        mockMvc.perform(get("/api/admin/reports").cookie(alice)).andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/admin/reports/1/resolve"), Map.of("resolution", "DISMISSED")).cookie(alice))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/status").cookie(alice))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.openReports").value(0));
    }

    @Test
    void adminsDismissOrDeleteTheReportedPost() throws Exception {
        Cookie admin = registerAndLogin("report_admin");
        Cookie alice = registerAndLogin();
        Cookie carol = registerAndLogin();
        Cookie bob = registerAndLogin();
        String spam = createPost(bob, "怪しいリンク");
        String fine = createPost(bob, "ふつうの投稿");
        mockMvc.perform(json(post("/api/reports"), report("postId", spam, "SPAM")).cookie(alice));
        mockMvc.perform(json(post("/api/reports"), report("postId", spam, "HARASSMENT")).cookie(carol));
        mockMvc.perform(json(post("/api/reports"), report("postId", fine, "OTHER")).cookie(alice));

        JsonNode open = openReports(admin);
        assertEquals(3, open.size());
        // 問題なし
        String fineReport = open.get(2).get("id").asString();
        mockMvc.perform(json(post("/api/admin/reports/" + fineReport + "/resolve"), Map.of("resolution", "DISMISSED"))
                .cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/" + fine).cookie(alice)).andExpect(status().isOk());

        // 投稿を消すと、その投稿への通報がまとめて片付く
        String spamReport = open.get(0).get("id").asString();
        mockMvc.perform(json(post("/api/admin/reports/" + spamReport + "/resolve"), Map.of("resolution", "POST_DELETED"))
                .cookie(admin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/posts/" + spam).cookie(alice)).andExpect(status().isNotFound());
        assertEquals(0, openReports(admin).size());

        JsonNode resolved = body(perform(get("/api/admin/reports?status=resolved").cookie(admin)));
        assertEquals(3, resolved.size());
        JsonNode deleted = resolved.get(0).get("resolution").asString().equals("POST_DELETED") ? resolved.get(0) : resolved.get(1);
        // 投稿は消えても、何を通報したかは残る
        assertTrue(deleted.get("post").get("id").isNull());
        assertEquals("怪しいリンク", deleted.get("post").get("body").asString());
        assertFalse(resolved.get(0).get("resolvedAt").isNull());

        // 投稿の通報でなければ消せない
        String userReportTarget = registerUserOnly();
        mockMvc.perform(json(post("/api/reports"), report("username", userReportTarget, "SPAM")).cookie(alice));
        String userReport = openReports(admin).get(0).get("id").asString();
        mockMvc.perform(json(post("/api/admin/reports/" + userReport + "/resolve"), Map.of("resolution", "POST_DELETED"))
                .cookie(admin)).andExpect(status().isBadRequest());
    }

    private String registerUserOnly() throws Exception {
        String username = newUsername();
        registerAndLogin(username);
        return username;
    }
}
