package com.snsapp.api.realtime;

import com.snsapp.api.auth.CurrentUser;
import com.snsapp.api.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.time.Instant;
import java.util.Map;

/**
 * /api/ws のつながり(F-28)。つなぐときにログインの Cookie(アクセストークン)を確かめ、
 * その期限が来たら閉じる。画面から届くのは生存確認("ping")だけで、何もしない
 */
@Component
public class RealtimeHandler extends TextWebSocketHandler implements HandshakeInterceptor {

    /** アクセストークンの期限が来た。画面は作り直してからつなぎ直す */
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "token expired");

    private static final String USER_KEY = "userId";
    private static final String EXPIRES_KEY = "expiresAt";
    // 遅い回線の画面に送れずに溜まっても、メモリを食い尽くさないように
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 64 * 1024;

    private final CurrentUser currentUser;
    private final RealtimeHub hub;

    public RealtimeHandler(CurrentUser currentUser, RealtimeHub hub) {
        this.currentUser = currentUser;
        this.hub = hub;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)) {
            return false;
        }
        HttpServletRequest http = servlet.getServletRequest();
        try {
            attributes.put(USER_KEY, currentUser.require(http));
            attributes.put(EXPIRES_KEY, currentUser.expiresAt(http));
            return true;
        } catch (ApiException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = (String) session.getAttributes().get(USER_KEY);
        Instant expiresAt = (Instant) session.getAttributes().get(EXPIRES_KEY);
        hub.add(new RealtimeHub.Connection(userId,
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES), expiresAt));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 生存確認("ping")。CloudFront などが、静かなつながりを切らないように画面が送ってくる
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.remove((String) session.getAttributes().get(USER_KEY), session);
    }

    /** 期限の来たつながりを1分ごとに閉じる */
    @Scheduled(fixedDelay = 60_000)
    public void closeExpired() {
        hub.closeExpired();
    }
}
