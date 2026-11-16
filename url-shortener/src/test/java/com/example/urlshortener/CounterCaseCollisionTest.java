package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.HashSet;
import java.util.Set;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "shortener.key-strategy=counter")
class CounterCaseCollisionTest extends IntegrationTestSupport {

    @Autowired
    ShortUrlRepository repository;

    @Test
    void counterSurvivesCaseCollidingIds() throws Exception {
        // 연속된 70개 id 창에는 끝자리가 소문자/대문자로만 다른 쌍(n, n+26)이
        // 시작 id 와 무관하게 항상 포함된다. 그래서 공유 DB 의 id 진행 상태와 상관없이
        // 대소문자만 다른 키 충돌 위험 구간을 반드시 지나간다
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 70; i++) {
            String body = mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com/counter/" + i + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            keys.add(read(body, "$.shortKey"));
        }

        // 모두 201 이었고 발급된 키가 서로 다르다
        assertThat(keys).hasSize(70);
    }
}
