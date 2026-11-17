package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShortenApiTest extends IntegrationTestSupport {

    private ResultActions postUrl(String url) throws Exception {
        return mockMvc.perform(post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + url + "\"}"));
    }

    private String shortKeyOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.shortKey");
    }

    @Test
    void shortensUrl() throws Exception {
        ResultActions result = postUrl("https://example.com/very/long/path")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortKey").value(org.hamcrest.Matchers.matchesPattern("^[0-9a-zA-Z]+$")));

        String shortKey = shortKeyOf(result);
        result.andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/" + shortKey));
    }

    @Test
    void sameUrlGetsNewKeyEachTime() throws Exception {
        String first = shortKeyOf(postUrl("https://example.com/same").andExpect(status().isCreated()));
        String second = shortKeyOf(postUrl("https://example.com/same").andExpect(status().isCreated()));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsBlankUrl() throws Exception {
        postUrl("").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingUrl() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonHttpScheme() throws Exception {
        postUrl("javascript:alert(1)").andExpect(status().isBadRequest());
        postUrl("ftp://example.com/a").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUrlWithoutHost() throws Exception {
        postUrl("http:///evil.com").andExpect(status().isBadRequest());
    }

    @Test
    void rejectsTooLongUrl() throws Exception {
        String url = "https://example.com/" + "a".repeat(2049 - 20);
        assertThat(url).hasSize(2049);

        postUrl(url).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsMaxLengthUrl() throws Exception {
        String url = "https://example.com/" + "a".repeat(2048 - 20);
        assertThat(url).hasSize(2048);

        postUrl(url).andExpect(status().isCreated());
    }

    @Test
    void rejectsMalformedUnicode() throws Exception {
        // JSON 이스케이프로 짝이 맞지 않는 UTF-16 서로게이트를 넣는다
        postUrl("https://example.com/\\ud800").andExpect(status().isBadRequest());
    }

    @Test
    void acceptsKoreanUrl() throws Exception {
        postUrl("https://example.com/한글").andExpect(status().isCreated());
    }
}
