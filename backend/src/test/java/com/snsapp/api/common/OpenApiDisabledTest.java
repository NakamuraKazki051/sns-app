package com.snsapp.api.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AWS と同じ設定(API_DOCS_ENABLED=false)では、API 仕様書を開けない */
@SpringBootTest(properties = "API_DOCS_ENABLED=false")
@AutoConfigureMockMvc
class OpenApiDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/index.html",
            "/webjars/swagger-ui/index.html"})
    void docsAreNotServed(String url) throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void apisStillWork() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
