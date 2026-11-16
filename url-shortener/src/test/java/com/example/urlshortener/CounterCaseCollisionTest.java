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
        // 공유 DB 라 id 가 이미 진행돼 있을 수 있다. id 10("a") 과 id 36("A") 경계를 반드시 지나가도록 한다
        // 공유 DB 라 id 가 이미 진행돼 있을 수 있다. 최소 70회, 그리고 id 36 을 넘을 때까지 요청해
        // 새 DB 에서는 id 10("a") 과 id 36("A") 경계를 반드시 지나가게 한다
        int requests = 70;
        Set<String> keys = new HashSet<>();
        int i = 0;
        while (i < requests || repository.findAll().stream().mapToLong(ShortUrl::getId).max().orElse(0L) <= 36L) {
            String body = mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com/counter/" + i + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            keys.add(read(body, "$.shortKey"));
            i++;
        }

        // 모두 201 이었고 발급된 키가 서로 다르다
        assertThat(keys).hasSize(i);
    }
}
