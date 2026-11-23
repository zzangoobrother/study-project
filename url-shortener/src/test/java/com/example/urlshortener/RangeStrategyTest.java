package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${shortener.range.feistel-secret}")
    long feistelSecret;

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
        String k1 = creator.create("https://example.com/d1");
        String k2 = creator.create("https://example.com/d2");

        // 블록 경계에 걸리면 번호가 연속이 아니므로 건너뛴다 (블록 10000 이라 드물다)
        assumeTrue(numberOf(k2, feistel) == numberOf(k1, feistel) + 1);

        assertThat(numberOf(k2, feistel)).isEqualTo(numberOf(k1, feistel) + 1);
    }

    @Test
    void skipsKeyAlreadyTakenByAnotherStrategy() {
        FeistelPermutation feistel = FeistelPermutation.forShortKey(feistelSecret);
        String k1 = creator.create("https://example.com/s1");
        long n = numberOf(k1, feistel);
        String taken = Base62.encodePadded(feistel.permute(n + 1), 7);
        String otherUrl = "https://example.com/other-strategy";
        repository.save(ShortUrl.create(otherUrl, taken));
        try {
            String k2 = creator.create("https://example.com/s2");

            long n2 = numberOf(k2, feistel);
            // n+1 은 이미 쓰인 키이므로 절대 나오면 안 된다
            assertThat(n2).isNotEqualTo(n + 1);
            // n+2 보다 크면 블록 경계에 걸린 것이므로 건너뛴다
            assumeTrue(n2 == n + 2);

            assertThat(k2).isEqualTo(Base62.encodePadded(feistel.permute(n + 2), 7));
            assertThat(repository.findByShortKey(taken).orElseThrow().getOriginalUrl()).isEqualTo(otherUrl);
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
