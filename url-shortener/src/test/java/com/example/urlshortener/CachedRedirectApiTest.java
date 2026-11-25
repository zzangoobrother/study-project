package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CachedRedirectApiTest extends IntegrationTestSupport {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void createdKeyIsInRedisRightAfterPost() throws Exception {
        String url = "https://example.com/cached";
        String body = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(body, "$.shortKey");

        assertThat(redis.opsForValue().get(RedisUrlCache.KEY_PREFIX + key)).isEqualTo(url);
        mockMvc.perform(get("/" + key))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, url));
    }

    @Test
    void unknownKeyLeavesNegativeOnlyInRedis() throws Exception {
        mockMvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound());

        assertThat(redis.opsForValue().get(RedisUrlCache.KEY_PREFIX + "zzzzzzz"))
                .isEqualTo(RedisUrlCache.NOT_FOUND_MARKER);
    }

    @Test
    void invalidKeyDoesNotWriteRedis() throws Exception {
        mockMvc.perform(get("/ab-c"))
                .andExpect(status().isNotFound());

        assertThat(redis.hasKey(RedisUrlCache.KEY_PREFIX + "ab-c")).isFalse();
    }
}
