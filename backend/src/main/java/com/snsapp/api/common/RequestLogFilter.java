package com.snsapp.api.common;

import com.snsapp.api.auth.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * API のリクエストごとに1件ログを出す。処理中のログにも同じ request_id を付けるので、1つのリクエストを追える。
 * 監視(CloudWatch)のアラームは、ここの「遅いリクエスト」を数えている。項目は docs/monitoring.md。
 * クエリ文字列には検索語が入るので出さない
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final List<String> FIELDS = List.of("request_id", "user_id", "method", "path", "status", "duration_ms");

    private final long slowRequestMs;

    public RequestLogFilter(@Value("${app.slow-request-ms:1000}") long slowRequestMs) {
        this.slowRequestMs = slowRequestMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        MDC.put("request_id", requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            write(request, failed ? 500 : response.getStatus(), (System.nanoTime() - startedAt) / 1_000_000);
            FIELDS.forEach(MDC::remove);
        }
    }

    private void write(HttpServletRequest request, int status, long durationMs) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        MDC.put("method", method);
        MDC.put("path", path);
        MDC.put("status", String.valueOf(status));
        MDC.put("duration_ms", String.valueOf(durationMs));
        if (request.getAttribute(CurrentUser.VERIFIED_KEY) instanceof String userId) {
            MDC.put("user_id", userId);
        }
        if (durationMs >= slowRequestMs) {
            log.warn("遅いリクエスト {} {} {}", method, path, status);
        } else {
            log.info("{} {} {}", method, path, status);
        }
    }
}
