package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "shortener.key-strategy=range")
class RangeStrategyTest extends IntegrationTestSupport {

    @Autowired
    ShortUrlCreator creator;

    @Autowired
    ShortUrlRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Value("${shortener.range.feistel-secret}")
    long feistelSecret;

    private long nextStart() {
        return jdbcTemplate.queryForObject(
                "SELECT next_start FROM key_range WHERE name = ?", Long.class, KeyRange.SHORT_URL);
    }

    private long numberOf(String key, FeistelPermutation feistel) {
        return feistel.inverse(Base62.decode(key));
    }

    @Test
    void rangeStrategyIsSelected() {
        assertThat(creator).isInstanceOf(RangeShortUrlCreator.class);
    }

    @Test
    void keysAreSevenBase62Chars() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String key = creator.create("https://example.com/k" + i);
            assertThat(key).matches("^[0-9a-zA-Z]{7}$");
            keys.add(key);
        }
        assertThat(keys).hasSize(200);
    }

    @Test
    void keyDecodesBackToIssuedNumber() {
        FeistelPermutation feistel = FeistelPermutation.forShortKey(feistelSecret);
        // 워밍업: 발급기가 범위를 들고 있게 한다
        creator.create("https://example.com/warmup");
        long before = nextStart();
        String k1 = creator.create("https://example.com/d1");
        String k2 = creator.create("https://example.com/d2");
        long after = nextStart();

        // 블록 경계 판정은 결과가 아니라 DB 상태로 한다. 그 사이 새 할당이 없었으면 같은 블록이다.
        assumeTrue(before == after);

        assertThat(numberOf(k2, feistel)).isEqualTo(numberOf(k1, feistel) + 1);
    }

    @Test
    void skipsKeyAlreadyTakenByAnotherStrategy() {
        FeistelPermutation feistel = FeistelPermutation.forShortKey(feistelSecret);
        // k1 생성이 워밍업을 겸한다
        String k1 = creator.create("https://example.com/s1");
        long n = numberOf(k1, feistel);
        String taken = Base62.encodePadded(feistel.permute(n + 1), 7);
        String otherUrl = "https://example.com/other-strategy";
        repository.save(ShortUrl.create(otherUrl, taken));
        try {
            long before = nextStart();
            String k2 = creator.create("https://example.com/s2");
            long after = nextStart();
            long n2 = numberOf(k2, feistel);

            // 무조건 검증: 미리 넣은 행은 그대로이고, n+1 은 건너뛴다
            assertThat(repository.findByShortKey(taken).orElseThrow().getOriginalUrl()).isEqualTo(otherUrl);
            assertThat(n2).isGreaterThan(n + 1);

            // 새 할당이 없었던 경우(같은 블록)에만 정확히 n+2 임을 확인한다
            assumeTrue(before == after);
            assertThat(n2).isEqualTo(n + 2);
        } finally {
            repository.findByShortKey(taken).ifPresent(repository::delete);
        }
    }

    @Test
    void paddedKeyRedirects() throws Exception {
        String key = Base62.encodePadded(12345, 7);
        String url = "https://example.com/padded";
        repository.save(ShortUrl.create(url, key));
        try {
            mockMvc.perform(get("/" + key))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", url));
        } finally {
            repository.findByShortKey(key).ifPresent(repository::delete);
        }
    }
}
