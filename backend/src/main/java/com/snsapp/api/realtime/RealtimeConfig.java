package com.snsapp.api.realtime;

import com.snsapp.api.common.WebConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket の入り口 /api/ws(F-28)。つないでよいオリジンは CORS と同じ(ほかのサイトの画面から、
 * ログイン中の人の Cookie でつながれないように)
 */
@Configuration
@EnableWebSocket
public class RealtimeConfig implements WebSocketConfigurer {

    public static final String PATH = "/api/ws";

    private final RealtimeHandler handler;
    private final WebConfig webConfig;

    public RealtimeConfig(RealtimeHandler handler, WebConfig webConfig) {
        this.handler = handler;
        this.webConfig = webConfig;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH)
                .addInterceptors(handler)
                .setAllowedOriginPatterns(webConfig.allowedOriginPatterns());
    }
}
