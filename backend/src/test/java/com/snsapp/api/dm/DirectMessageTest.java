package com.snsapp.api.dm;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** DM(F-23) */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DirectMessageTest extends ApiTestSupport {

    @Autowired
    private DirectMessageRepository messages;

    @Autowired
    private UserRepository users;

    private MvcResult send(Cookie from, String to, String body) throws Exception {
        return perform(json(post("/api/dm/" + to).cookie(from), Map.of("body", body)));
    }

    private String sendOk(Cookie from, String to, String body) throws Exception {
        MvcResult result = send(from, to, body);
        assertEquals(201, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return body(result).get("id").asString();
    }

    /** 新しい順の本文 */
    private List<String> bodies(Cookie me, String partner) throws Exception {
        JsonNode page = body(perform(get("/api/dm/" + partner).cookie(me)));
        List<String> result = new ArrayList<>();
        page.get("messages").forEach(m -> result.add(m.get("body").asString()));
        return result;
    }

    private long unread(Cookie me) throws Exception {
        return body(perform(get("/api/dm/unread-count").cookie(me))).get("count").asLong();
    }

    @Test
    void sendReadAndListConversations() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        String carolName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin(bobName);
        Cookie carol = registerAndLogin(carolName);

        sendOk(alice, bobName, "こんにちは");
        sendOk(bob, aliceName, "やあ");
        sendOk(carol, aliceName, "はじめまして");
        sendOk(carol, aliceName, "よろしく");

        assertEquals(3, unread(alice));
        assertEquals(0, unread(carol));
        // 新しい会話から。未読の数は相手ごと
        mockMvc.perform(get("/api/dm").cookie(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].partner.username").value(carolName))
                .andExpect(jsonPath("$[0].lastMessage.body").value("よろしく"))
                .andExpect(jsonPath("$[0].lastMessage.mine").value(false))
                .andExpect(jsonPath("$[0].unreadCount").value(2))
                .andExpect(jsonPath("$[1].partner.username").value(bobName))
                .andExpect(jsonPath("$[1].unreadCount").value(1));

        // 開くと相手から届いた分が既読になる。相手から見ると、自分の送った分が「既読」
        assertEquals(List.of("よろしく", "はじめまして"), bodies(alice, carolName));
        assertEquals(1, unread(alice));
        mockMvc.perform(get("/api/dm/" + aliceName).cookie(carol))
                .andExpect(jsonPath("$.messages[0].mine").value(true))
                .andExpect(jsonPath("$.messages[0].read").value(true));
        assertEquals(List.of("やあ", "こんにちは"), bodies(bob, aliceName));
        // 2人の間のものだけ
        assertEquals(List.of(), bodies(bob, carolName));
    }

    @Test
    void readingAgainShowsNewMessagesAndTheReadMark() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin(bobName);
        sendOk(alice, bobName, "1");
        // bob が開くまでは未読
        mockMvc.perform(get("/api/dm/" + bobName).cookie(alice)).andExpect(jsonPath("$.messages[0].read").value(false));

        // 開いている間に届いたものも、聞き直すと既読になる
        mockMvc.perform(get("/api/dm/" + aliceName).cookie(bob)).andExpect(status().isOk());
        sendOk(bob, aliceName, "2");
        assertEquals(List.of("2", "1"), bodies(alice, bobName));
        assertEquals(0, unread(alice));
        mockMvc.perform(get("/api/dm/" + bobName).cookie(alice))
                .andExpect(jsonPath("$.messages[1].mine").value(true))
                .andExpect(jsonPath("$.messages[1].read").value(true));
    }

    @Test
    void pagesHoldThirtyAndContinueWithBefore() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        registerAndLogin(bobName);
        // 送る API は1分に20通までなので、DB に直接入れる
        String aliceId = users.findByUsername(aliceName).orElseThrow().getId();
        String bobId = users.findByUsername(bobName).orElseThrow().getId();
        for (int i = 0; i < DirectMessageController.PAGE_SIZE + 1; i++) {
            messages.save(new DirectMessage(aliceId, bobId, "m" + i));
        }
        JsonNode first = body(perform(get("/api/dm/" + bobName).cookie(alice)));
        assertEquals(DirectMessageController.PAGE_SIZE, first.get("messages").size());
        assertEquals(true, first.get("hasMore").asBoolean());
        mockMvc.perform(get("/api/dm/" + bobName).param("before", first.get("nextCursor").asString()).cookie(alice))
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].body").value("m0"))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void invalidMessagesAreRejected() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        registerAndLogin(bobName);

        assertEquals(400, send(alice, aliceName, "自分へ").getResponse().getStatus());
        assertEquals(400, send(alice, bobName, "   ").getResponse().getStatus());
        assertEquals(400, send(alice, bobName, "あ".repeat(DirectMessage.BODY_MAX + 1)).getResponse().getStatus());
        // 絵文字も1字と数える
        sendOk(alice, bobName, "👍".repeat(DirectMessage.BODY_MAX));
        assertEquals(404, send(alice, "nobody_here_x", "やあ").getResponse().getStatus());
        assertEquals(404, send(alice, "bad-name!", "やあ").getResponse().getStatus());
        // 前後の空白は落とす
        mockMvc.perform(json(post("/api/dm/" + bobName).cookie(alice), Map.of("body", "  やあ \n")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("やあ"));
    }

    @Test
    void sendingTooFastIsLimited() throws Exception {
        String bobName = newUsername();
        Cookie alice = registerAndLogin();
        registerAndLogin(bobName);
        for (int i = 0; i < DirectMessageController.SENDS_PER_WINDOW; i++) {
            sendOk(alice, bobName, "m" + i);
        }
        assertEquals(429, send(alice, bobName, "もう1通").getResponse().getStatus());
    }

    @Test
    void blockingHidesTheConversationBothWaysButMutingDoesNot() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        String carolName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin(bobName);
        Cookie carol = registerAndLogin(carolName);
        sendOk(bob, aliceName, "bob から");
        sendOk(carol, aliceName, "carol から");

        mockMvc.perform(post("/api/users/" + bobName + "/block").cookie(alice)).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/" + carolName + "/mute").cookie(alice)).andExpect(status().isOk());

        mockMvc.perform(get("/api/dm").cookie(alice))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].partner.username").value(carolName));
        assertEquals(1, unread(alice));
        assertEquals(404, send(alice, bobName, "やあ").getResponse().getStatus());
        assertEquals(404, send(bob, aliceName, "やあ").getResponse().getStatus());
        assertEquals(404, perform(get("/api/dm/" + aliceName).cookie(bob)).getResponse().getStatus());
        mockMvc.perform(get("/api/dm").cookie(bob)).andExpect(jsonPath("$.length()").value(0));

        // ブロックを外すと、前のやりとりもまた見える
        mockMvc.perform(delete("/api/users/" + bobName + "/block").cookie(alice)).andExpect(status().isOk());
        assertEquals(List.of("bob から"), bodies(alice, bobName));
    }

    @Test
    void deletingAnAccountRemovesItsMessages() throws Exception {
        String aliceName = newUsername();
        String bobName = newUsername();
        Cookie alice = registerAndLogin(aliceName);
        Cookie bob = registerAndLogin(bobName);
        sendOk(bob, aliceName, "消える");
        mockMvc.perform(json(delete("/api/users/me").cookie(bob), Map.of("password", PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/dm").cookie(alice)).andExpect(jsonPath("$.length()").value(0));
        assertEquals(0, unread(alice));
    }
}
