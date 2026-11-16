package com.example.urlshortener;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

    private final ShortUrlRepository repository;
    private final ShortUrlCreator shortUrlCreator;

    public ShortUrlService(ShortUrlRepository repository, ShortUrlCreator shortUrlCreator) {
        this.repository = repository;
        this.shortUrlCreator = shortUrlCreator;
    }

    // 트랜잭션 경계는 구현체(ShortUrlCreator)가 소유한다
    public String shorten(String originalUrl) {
        return shortUrlCreator.create(originalUrl);
    }

    @Transactional(readOnly = true)
    public String getOriginalUrl(String shortKey) {
        return repository.findByShortKey(shortKey)
                .map(ShortUrl::getOriginalUrl)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortKey));
    }
}
