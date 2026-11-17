package com.snsapp.api.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * つながっている画面(WebSocket)に「何かが変わった」を知らせる(F-28、docs/spec.md 4.14)。
 * 送るのは種類だけで、中身は画面がいつもの API で読み直す(権限の確かめを API に任せられる)。
 * つながりはアプリのメモリに持つ(EC2 は1台なので足りる。台数を増やすなら Redis などで配る)
 */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);

    /** 知らせの種類。画面はこれを見て、どの数・一覧を読み直すか決める */
    public enum Event {
        /** 通知が増えた・減った(未読の数を読み直す) */
        NOTIFICATION,
        /** DM が届いた・取り消された・読まれた(未読の数と、開いている会話を読み直す)。from は相手のユーザー名 */
        DM,
        /** 誰かが投稿した(タイムラインの新着の数を読み直す) */
        POST
    }

    record Connection(String userId, WebSocketSession session, Instant expiresAt) {
    }

    private final Map<String, Set<Connection>> byUser = new ConcurrentHashMap<>();
    private final ObjectMapper json;
    private Clock clock = Clock.systemUTC();

    public RealtimeHub(ObjectMapper json) {
        this.json = json;
    }

    void add(Connection connection) {
        byUser.computeIfAbsent(connection.userId(), id -> ConcurrentHashMap.newKeySet()).add(connection);
    }

    void remove(String userId, WebSocketSession session) {
        byUser.computeIfPresent(userId, (id, set) -> {
            set.removeIf(c -> c.session().getId().equals(session.getId()));
            return set.isEmpty() ? null : set;
        });
    }

    /** その人の画面すべてに知らせる。DB の変更が確定してから送る(先に送ると、読み直しても前の状態が見える) */
    public void send(String userId, Event event, String from) {
        afterCommit(() -> {
            Set<Connection> connections = byUser.get(userId);
            if (connections != null) {
                String message = message(event, from);
                connections.forEach(c -> deliver(c, message));
            }
        });
    }

    public void send(String userId, Event event) {
        send(userId, event, null);
    }

    /** つながっている全員に知らせる(新しい投稿。見えるかどうかは、画面が新着の数を聞いたときに API が決める) */
    public void broadcast(Event event) {
        afterCommit(() -> {
            String message = message(event, null);
            byUser.values().forEach(set -> set.forEach(c -> deliver(c, message)));
        });
    }

    /**
     * ログインの期限(アクセストークンの期限)を過ぎたつながりを閉じる。画面は作り直したトークンでつなぎ直す。
     * ログアウト・パスワード変更のあとも、遅くともアクセストークンの期限(15分)で止まる
     */
    int closeExpired() {
        Instant now = clock.instant();
        int closed = 0;
        for (Set<Connection> set : byUser.values()) {
            for (Connection c : set) {
                if (!c.expiresAt().isAfter(now)) {
                    close(c, RealtimeHandler.TOKEN_EXPIRED);
                    remove(c.userId(), c.session());
                    closed++;
                }
            }
        }
        return closed;
    }

    /** その人のつながりをすべて閉じる(ログアウト・端末のログアウトのとき) */
    public void closeAll(String userId) {
        Set<Connection> connections = byUser.remove(userId);
        if (connections != null) {
            connections.forEach(c -> close(c, RealtimeHandler.TOKEN_EXPIRED));
        }
    }

    int connectionCount() {
        return byUser.values().stream().mapToInt(Set::size).sum();
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    Instant now() {
        return clock.instant();
    }

    private String message(Event event, String from) {
        return from == null
                ? json.writeValueAsString(Map.of("type", event.name()))
                : json.writeValueAsString(Map.of("type", event.name(), "from", from));
    }

    private void deliver(Connection connection, String message) {
        try {
            if (connection.session().isOpen()) {
                connection.session().sendMessage(new TextMessage(message));
            }
        } catch (IOException | IllegalStateException e) {
            // 切れかけのつながり。画面はつなぎ直すので、ここでは片付けるだけ
            log.debug("リアルタイムの知らせを送れませんでした: {}", e.getMessage());
            remove(connection.userId(), connection.session());
        }
    }

    private void close(Connection connection, CloseStatus status) {
        try {
            connection.session().close(status);
        } catch (IOException e) {
            log.debug("つながりを閉じられませんでした: {}", e.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
