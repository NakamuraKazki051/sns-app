package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import com.snsapp.api.common.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.register.per-hour=2")
@AutoConfigureMockMvc
@Transactional
class RegisterLimitTest extends ApiTestSupport {

    @Autowired
    private RateLimiter rateLimiter;

    @AfterEach
    void resetClock() {
        rateLimiter.setClock(Clock.systemUTC());
    }

    private ResultActions registerFrom(String ip, Map<String, Object> params) throws Exception {
        return mockMvc.perform(json(post("/api/auth/register"), params).with(request -> {
            request.setRemoteAddr(ip);
            return request;
        }));
    }

    private ResultActions registerFrom(String ip) throws Exception {
        return registerFrom(ip, registerParams(newUsername()));
    }

    @Test
    void oneIpCanRegisterOnlyAFewTimesAnHour() throws Exception {
        registerFrom("198.51.100.1").andExpect(status().isCreated());
        registerFrom("198.51.100.1").andExpect(status().isCreated());
        registerFrom("198.51.100.1").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("登録の回数が多すぎます。1時間ほど待ってからもう一度お試しください"));

        // 他の人は登録できる
        registerFrom("198.51.100.2").andExpect(status().isCreated());

        rateLimiter.setClock(Clock.fixed(Instant.now().plus(Duration.ofHours(1)).plusSeconds(1), ZoneOffset.UTC));
        registerFrom("198.51.100.1").andExpect(status().isCreated());
    }

    @Test
    void inputMistakesDoNotUseUpTheLimit() throws Exception {
        Map<String, Object> mistake = new HashMap<>(registerParams(newUsername()));
        mistake.put("password", "short");
        for (int i = 0; i < 3; i++) {
            registerFrom("198.51.100.3", mistake).andExpect(status().isBadRequest());
        }
        registerFrom("198.51.100.3").andExpect(status().isCreated());
    }

    @Test
    void takenEmailsAreCountedSoTheyCannotBeProbedWithoutLimit() throws Exception {
        Map<String, Object> taken = registerParams(newUsername());
        registerFrom("198.51.100.4", taken).andExpect(status().isCreated());
        registerFrom("198.51.100.4", taken).andExpect(status().isConflict());
        registerFrom("198.51.100.4", taken).andExpect(status().isTooManyRequests());
    }
}
