package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 부모의 테스트를 md5 전략으로 다시 실행한다
@TestPropertySource(properties = "shortener.key-strategy=md5")
class Md5ShortenApiTest extends ShortenApiTest {

    // md5 는 중복 제거하므로 반대 동작: 같은 URL 은 항상 같은 키를 받는다
    @Override
    @Test
    @DisplayName("md5 는 같은 URL 에 같은 키를 준다 (부모와 반대 동작)")
    void sameUrlGetsNewKeyEachTime() throws Exception {
        String first = postAndGetKey("https://example.com/same");
        String second = postAndGetKey("https://example.com/same");

        assertThat(first).isEqualTo(second);
    }

    private String postAndGetKey(String url) throws Exception {
        String body = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + url + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.shortKey");
    }
}
