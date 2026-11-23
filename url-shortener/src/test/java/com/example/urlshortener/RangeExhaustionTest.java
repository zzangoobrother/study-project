package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 별도 클래스로 둔 이유: 싱글톤 발급기가 이미 받아 둔 범위를 메모리에 들고 있으면 next_start 를 끝으로 옮겨도 503 이 나지 않는다.
// block-size=1 이면 요청마다 할당을 시도하므로, 같은 컨텍스트를 쓰는 이 클래스 안에서만 결정적으로 검증할 수 있다.
@TestPropertySource(properties = {"shortener.key-strategy=range", "shortener.range.block-size=1"})
class RangeExhaustionTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void returns503WhenKeySpaceExhausted() throws Exception {
        Long original = jdbcTemplate.queryForObject(
                "SELECT next_start FROM key_range WHERE name = ?", Long.class, KeyRange.SHORT_URL);
        try {
            jdbcTemplate.update("UPDATE key_range SET next_start = ? WHERE name = ?",
                    FeistelPermutation.SHORT_KEY_SPACE, KeyRange.SHORT_URL);

            mockMvc.perform(post("/api/urls")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"https://example.com/exhausted\"}"))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            jdbcTemplate.update("UPDATE key_range SET next_start = ? WHERE name = ?", original, KeyRange.SHORT_URL);
        }
    }
}
