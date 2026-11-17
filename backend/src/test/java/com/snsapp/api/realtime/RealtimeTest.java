package com.snsapp.api.realtime;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.auth.CurrentUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WebSocket での知らせ(F-28)。本物のサーバーを立ててつなぐので、トランザクションで包まず、最後に片付ける */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RealtimeTest extends ApiTestSupport {

    private static final String ORIGIN = "http://localhost:5173";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RealtimeHub hub;

    private final List<String> usernames = new ArrayList<>();
    private final List<WebSocket> sockets = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        sockets.forEach(WebSocket::abort);
        hub.setClock(Clock.systemUTC());
        // フォローと投稿は DB が一緒に消さないので先に消す。DM・通知・ログインの記録はユーザーと一緒に消える
        for (String name : usernames) {
            jdbc.update("delete from follows where follower_id in (select id from users where username = ?)"
                    + " or followee_id in (select id from users where username = ?)", name, name);
            jdbc.update("delete from posts where user_id in (select id from users where username = ?)", name);
            jdbc.update("delete from users where username = ?", name);
        }
    }

    private record Received(WebSocket socket, BlockingQueue<String> messages, CompletableFuture<Integer> closed) {
        String next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "知らせが届かない");
            return message;
        }
    }

    private Cookie user(String username) throws Exception {
        usernames.add(username);
        return registerAndLogin(username);
    }

    private Received connect(Cookie session, String origin) throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder().header("Origin", origin);
        if (session != null) {
            builder.header("Cookie", CurrentUser.COOKIE_NAME + "=" + session.getValue());
        }
        WebSocket socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/api/ws"), new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messages.add(data.toString());
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                closed.complete(statusCode);
                return null;
            }
        }).get(5, TimeUnit.SECONDS);
        sockets.add(socket);
        return new Received(socket, messages, closed);
    }

    @Test
    void connectingNeedsLoginAndAnAllowedOrigin() throws Exception {
        ExecutionException noLogin = assertThrows(ExecutionException.class, () -> connect(null, ORIGIN));
        assertInstanceOf(WebSocketHandshakeException.class, noLogin.getCause());
        assertEquals(401, ((WebSocketHandshakeException) noLogin.getCause()).getResponse().statusCode());

        Cookie alice = user(newUsername());
        ExecutionException otherSite = assertThrows(ExecutionException.class, () -> connect(alice, "https://evil.example"));
        assertEquals(403, ((WebSocketHandshakeException) otherSite.getCause()).getResponse().statusCode());
    }

    @Test
    void dmsNotificationsAndPostsArriveAtOnce() throws Exception {
        String alice = newUsername();
        Cookie aliceSession = user(alice);
        String bob = newUsername();
        Cookie bobSession = user(bob);
        Received bobScreen = connect(bobSession, ORIGIN);

        mockMvc.perform(json(post("/api/dm/" + bob), Map.of("body", "こんにちは")).cookie(aliceSession))
                .andExpect(status().isCreated());
        assertEquals("{\"type\":\"DM\",\"from\":\"" + alice + "\"}", normalize(bobScreen.next()));

        mockMvc.perform(post("/api/users/" + bob + "/follow").cookie(aliceSession)).andExpect(status().isOk());
        assertEquals("{\"type\":\"NOTIFICATION\"}", bobScreen.next());

        String postId = createPost(aliceSession, "みんなへ");
        assertEquals("{\"type\":\"POST\"}", bobScreen.next());
        mockMvc.perform(delete("/api/posts/" + postId).cookie(aliceSession));
    }

    @Test
    void connectionsCloseWhenTheAccessTokenExpires() throws Exception {
        Received screen = connect(user(newUsername()), ORIGIN);
        hub.setClock(Clock.fixed(hub.now().plus(Duration.ofMinutes(16)), ZoneOffset.UTC));
        assertTrue(hub.closeExpired() >= 1);
        assertEquals(4001, screen.closed().get(5, TimeUnit.SECONDS));
    }

    /** Jackson は Map の順番を保たないことがあるので、DM の知らせは type → from の順に並べ直す */
    private static String normalize(String message) {
        if (message.startsWith("{\"from\"")) {
            String from = message.replaceAll(".*\"from\":\"([^\"]+)\".*", "$1");
            return "{\"type\":\"DM\",\"from\":\"" + from + "\"}";
        }
        return message;
    }
}
