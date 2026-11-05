package com.snsapp.api.common;

import com.snsapp.api.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 仕様書が、リポジトリの docs/openapi.yaml と実際の動きからずれていないことを確かめる */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocTest extends ApiTestSupport {

    private static final Path DOC_FILE = Path.of("../docs/openapi.yaml");

    @Test
    void docsFileMatchesTheCode() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(DOC_FILE, generated);
        }
        // Windows は Git から取り出すと改行が CRLF になる
        assertEquals(Files.readString(DOC_FILE).replace("\r\n", "\n"), generated.replace("\r\n", "\n"),
                "docs/openapi.yaml が古いです。backend で mvn test -Dtest=OpenApiDocTest -Dopenapi.update=true を実行してください");
    }

    @Test
    void onlyTheDocumentedApisWorkWithoutLogin() throws Exception {
        JsonNode paths = body(perform(get("/v3/api-docs"))).get("paths");
        Set<String> publicApis = new HashSet<>();
        int needLogin = 0;
        for (Map.Entry<String, JsonNode> path : paths.properties()) {
            for (Map.Entry<String, JsonNode> entry : path.getValue().properties()) {
                JsonNode operation = entry.getValue();
                String name = entry.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey();
                assertTrue(operation.hasNonNull("summary"), name + " に説明がありません");
                if (operation.has("security")) {
                    publicApis.add(name);
                    continue;
                }
                needLogin++;
                assertEquals(401, mockMvc.perform(requestWithoutLogin(entry.getKey(), path.getKey(), operation)).andReturn()
                        .getResponse().getStatus(), name + " は仕様書ではログインが要ることになっています");
                assertTrue(operation.get("responses").has("401"), name + " の仕様書に 401 がありません");
            }
        }
        assertEquals(Set.of("POST /api/auth/register", "POST /api/auth/login", "POST /api/auth/logout",
                "POST /api/auth/verify-email", "POST /api/auth/resend-verification"), publicApis);
        assertEquals(28, needLogin);
    }

    /** 入力の確認で先に 400 にならないよう、仕様書の必須の項目を埋める */
    private static AbstractMockHttpServletRequestBuilder<?> requestWithoutLogin(String method, String path,
                                                                                JsonNode operation) {
        String url = path.replace("{id}", "1").replace("{postId}", "1").replace("{username}", "nobody");
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase(Locale.ROOT));
        JsonNode content = operation.path("requestBody").path("content");
        AbstractMockHttpServletRequestBuilder<?> request;
        if (content.has("multipart/form-data") && !content.has("application/json")) {
            request = multipart(httpMethod, url).file(new MockMultipartFile("icon", new byte[]{1}));
        } else {
            request = request(httpMethod, url);
        }
        if (content.has("application/json")) {
            request.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"本文\",\"currentPassword\":\"pass\"}");
        }
        for (JsonNode parameter : operation.path("parameters")) {
            if ("query".equals(parameter.get("in").asString()) && parameter.path("required").asBoolean()) {
                request.param(parameter.get("name").asString(), "1");
            }
        }
        return request;
    }
}
