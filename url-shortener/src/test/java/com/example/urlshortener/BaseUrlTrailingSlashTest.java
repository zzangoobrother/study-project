package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "shortener.base-url=http://localhost:8080/")
class BaseUrlTrailingSlashTest extends IntegrationTestSupport {

    @Test
    void baseUrlTrailingSlashIsNormalized() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\"}"))
                .andExpect(status().isCreated());

        String body = result.andReturn().getResponse().getContentAsString();
        String shortKey = JsonPath.read(body, "$.shortKey");
        String shortUrl = JsonPath.read(body, "$.shortUrl");

        assertThat(shortUrl).isEqualTo("http://localhost:8080/" + shortKey);
    }
}
