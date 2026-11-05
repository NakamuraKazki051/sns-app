package com.snsapp.api.common;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AWS では CloudFront が https で受け、アプリへは http で渡す。ブラウザの Origin は https のままなので、
 * 同じサイトからの POST でもアプリには別のサイトに見える。APP_CORS_ALLOWED_ORIGINS に入れないと 403 になる
 */
@SpringBootTest(properties = "app.cors.allowed-origins=https://allowed.cloudfront.net")
@AutoConfigureMockMvc
@Transactional
class HttpsOriginTest extends ApiTestSupport {

    private ResultActions loginBehindCloudFront(String host) throws Exception {
        return mockMvc.perform(json(post("/api/auth/login"), Map.of("email", "nobody@example.com", "password", PASSWORD))
                .header("Origin", "https://" + host)
                .with(request -> {
                    request.setServerName(host);
                    request.setServerPort(80);
                    return request;
                }));
    }

    @Test
    void acceptsPostsFromTheConfiguredHttpsOrigin() throws Exception {
        // 401 は「認証まで届いた」しるし(このメールアドレスは登録されていない)
        loginBehindCloudFront("allowed.cloudfront.net").andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPostsFromAnHttpsOriginThatIsNotConfigured() throws Exception {
        loginBehindCloudFront("other.cloudfront.net").andExpect(status().isForbidden());
    }
}
