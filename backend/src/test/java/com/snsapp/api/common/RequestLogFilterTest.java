package com.snsapp.api.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.snsapp.api.auth.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** リクエストのログ。監視(Datadog)の条件はこの項目を見ている */
class RequestLogFilterTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
    // ログの項目は読むときに取られるので、片付けられる前に写しておく
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach
    void capture() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logs);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    @Test
    void recordsTheRequestAndWhoSentIt() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestLogFilter(1000).doFilter(request, response, (req, res) -> {
            req.setAttribute(CurrentUser.VERIFIED_KEY, "42");
            ((HttpServletResponse) res).setStatus(201);
        });

        assertEquals(1, logs.list.size());
        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("POST /api/posts 201", event.getFormattedMessage());
        Map<String, String> fields = event.getMDCPropertyMap();
        assertEquals("POST", fields.get("method"));
        assertEquals("/api/posts", fields.get("path"));
        assertEquals("201", fields.get("status"));
        assertEquals("42", fields.get("user_id"));
        assertNotNull(fields.get("duration_ms"));
        assertEquals(fields.get("request_id"), response.getHeader("X-Request-Id"));
    }

    @Test
    void theRequestIdIsOnEveryLogWhileHandling() throws Exception {
        AtomicReference<String> during = new AtomicReference<>();

        new RequestLogFilter(1000).doFilter(request("GET", "/api/timeline"), new MockHttpServletResponse(),
                (req, res) -> during.set(MDC.get("request_id")));

        assertNotNull(during.get());
        assertNull(MDC.get("request_id"));
    }

    @Test
    void leavesOutTheQueryString() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/users/search");
        request.setQueryString("q=secret-word");

        new RequestLogFilter(1000).doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        ILoggingEvent event = logs.list.get(0);
        assertFalse(event.getFormattedMessage().contains("secret-word"));
        assertFalse(event.getMDCPropertyMap().toString().contains("secret-word"));
    }

    @Test
    void slowRequestsAreWarnings() throws Exception {
        new RequestLogFilter(0).doFilter(request("GET", "/api/timeline"), new MockHttpServletResponse(), (req, res) -> { });

        ILoggingEvent event = logs.list.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().startsWith("遅いリクエスト"));
    }

    @Test
    void screenFilesAreNotRecorded() throws Exception {
        new RequestLogFilter(1000).doFilter(request("GET", "/assets/index.js"), new MockHttpServletResponse(), (req, res) -> { });

        assertTrue(logs.list.isEmpty());
    }

    @Test
    void aFailedRequestIsStillRecorded() {
        assertThrows(IllegalStateException.class, () ->
                new RequestLogFilter(1000).doFilter(request("GET", "/api/timeline"), new MockHttpServletResponse(), (req, res) -> {
                    throw new IllegalStateException("失敗");
                }));

        assertEquals("500", logs.list.get(0).getMDCPropertyMap().get("status"));
        assertNull(MDC.get("request_id"));
    }
}
