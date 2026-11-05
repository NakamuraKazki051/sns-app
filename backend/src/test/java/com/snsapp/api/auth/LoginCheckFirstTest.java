package com.snsapp.api.auth;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ログインしていなければ、入力の中身を見る前に 401 を返す(どんな入力なら通るかを、ログインなしで調べられないように) */
@SpringBootTest
@AutoConfigureMockMvc
class LoginCheckFirstTest extends ApiTestSupport {

    @Test
    void invalidInputWithoutLoginIs401() throws Exception {
        mockMvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/me/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/posts").contentType(MediaType.APPLICATION_JSON).content("{壊れた JSON"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts/abc")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicApisStillValidateInput() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
