package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedirectApiTest extends IntegrationTestSupport {

    private String createShortKey(String url) throws Exception {
        String body = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.shortKey");
    }

    @Test
    void redirectsWith302() throws Exception {
        String originalUrl = "https://example.com/very/long/path";
        String shortKey = createShortKey(originalUrl);

        mockMvc.perform(get("/" + shortKey))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));
    }

    @Test
    void redirectsFromDbWhenCacheIsCold() throws Exception {
        String originalUrl = "https://example.com/cold/cache";
        String shortKey = createShortKey(originalUrl);
        clearCaches();

        mockMvc.perform(get("/" + shortKey))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));
    }

    @Test
    void redirectPreservesQueryString() throws Exception {
        String originalUrl = "https://example.com/a?b=1&c=2";
        String shortKey = createShortKey(originalUrl);

        mockMvc.perform(get("/" + shortKey))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, originalUrl));
    }

    @Test
    void unknownKeyReturns404() throws Exception {
        mockMvc.perform(get("/zzzzzz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownKeyWithInvalidCharsReturns404() throws Exception {
        mockMvc.perform(get("/ab-c"))
                .andExpect(status().isNotFound());
    }

    // short_key 는 ascii 컬럼이라 비 ASCII 값을 그대로 조회하면 DB 가 collation 오류를 낸다
    @Test
    void nonAsciiKeyReturns404() throws Exception {
        mockMvc.perform(get(URI.create("/%ED%95%9C%EA%B8%80")))
                .andExpect(status().isNotFound());
    }

    @Test
    void tooLongKeyReturns404() throws Exception {
        mockMvc.perform(get("/" + "a".repeat(17)))
                .andExpect(status().isNotFound());
    }
}
