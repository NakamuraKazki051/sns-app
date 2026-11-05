package com.snsapp.api.common;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AWS と同じ設定で、自分の CloudFront を通ったリクエストだけを受け付けることと、本当の IP で数えることを確かめる */
@SpringBootTest(properties = {"app.origin-secret=right-secret", "app.register.per-hour=1"})
@AutoConfigureMockMvc
@Transactional
class OriginSecretTest extends ApiTestSupport {

    private ResultActions registerVia(String forwardedFor) throws Exception {
        return mockMvc.perform(json(post("/api/auth/register"), registerParams(newUsername()))
                .header(OriginSecretFilter.HEADER, "right-secret")
                .header("X-Forwarded-For", forwardedFor));
    }

    @Test
    void requestsWithoutTheSecretAreRefused() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/me").header(OriginSecretFilter.HEADER, "wrong")).andExpect(status().isForbidden());
        mockMvc.perform(get("/")).andExpect(status().isForbidden());
    }

    @Test
    void requestsWithTheSecretReachTheApp() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(OriginSecretFilter.HEADER, "right-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theFakeableStartOfXForwardedForDoesNotEscapeTheLimit() throws Exception {
        registerVia("203.0.113.9").andExpect(status().isCreated());
        // 利用者が先頭に嘘の IP を書いても、最後の本当の IP で数える
        registerVia("1.1.1.1, 203.0.113.9").andExpect(status().isTooManyRequests());
        registerVia("2.2.2.2, 3.3.3.3, 203.0.113.9").andExpect(status().isTooManyRequests());
        registerVia("203.0.113.10").andExpect(status().isCreated());
    }
}
