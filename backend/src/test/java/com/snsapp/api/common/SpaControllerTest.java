package com.snsapp.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void screenUrlsReturnTheReactApp() throws Exception {
        for (String path : new String[]{"/login", "/search", "/settings", "/posts/12", "/@alice_01",
                "/@alice_01/following", "/@alice_01/followers"}) {
            mockMvc.perform(get(path)).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void apiAndUnknownUrlsAreNotForwarded() throws Exception {
        mockMvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/posts/abc")).andExpect(status().isNotFound());
        mockMvc.perform(get("/@bad-name")).andExpect(status().isNotFound());
    }
}
