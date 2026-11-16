package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "shortener.key-strategy=counter")
class CounterStrategySelectionTest extends IntegrationTestSupport {

    @Autowired
    ShortUrlCreator creator;

    @Autowired
    ShortUrlRepository repository;

    @Test
    void counterStrategyIsSelected() {
        assertThat(creator).isInstanceOf(CounterShortUrlCreator.class);
    }

    @Test
    void counterKeyIsBase62OfId() {
        String key = creator.create("https://example.com/counter");

        ShortUrl saved = repository.findByShortKey(key).orElseThrow();
        assertThat(key).isEqualTo(Base62.encode(saved.getId()));
    }
}
