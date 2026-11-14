package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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
}
