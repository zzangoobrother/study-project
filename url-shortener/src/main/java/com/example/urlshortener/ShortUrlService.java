package com.example.urlshortener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

    private final ShortUrlRepository repository;

    public ShortUrlService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public String shorten(String originalUrl) {
        // IDENTITY INSERT 로 id 를 발급받은 뒤 Base62 로 인코딩해 키로 사용한다
        ShortUrl saved = repository.save(ShortUrl.create(originalUrl));
        String shortKey = Base62.encode(saved.getId());
        // 변경 감지로 UPDATE 된다
        saved.assignShortKey(shortKey);
        return shortKey;
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortKey) {
        return repository.findByShortKey(shortKey)
                .map(ShortUrl::getOriginalUrl)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortKey));
    }
}
