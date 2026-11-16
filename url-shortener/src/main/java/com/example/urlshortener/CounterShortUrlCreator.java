package com.example.urlshortener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "counter", matchIfMissing = true)
class CounterShortUrlCreator implements ShortUrlCreator {

    private final ShortUrlRepository repository;

    CounterShortUrlCreator(ShortUrlRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public String create(String originalUrl) {
        // IDENTITY INSERT 로 id 를 발급받은 뒤 Base62 로 인코딩해 키로 사용한다
        ShortUrl saved = repository.save(ShortUrl.create(originalUrl));
        String shortKey = Base62.encode(saved.getId());
        // 변경 감지로 UPDATE 된다
        saved.assignShortKey(shortKey);
        return shortKey;
    }
}
