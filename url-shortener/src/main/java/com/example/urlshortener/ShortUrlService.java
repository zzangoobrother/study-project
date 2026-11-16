package com.example.urlshortener;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

    // short_key 컬럼은 ascii 라 비 ASCII 조회값은 DB 에서 collation 오류가 난다. 조회 전에 형식을 걸러낸다
    private static final Pattern SHORT_KEY_PATTERN = Pattern.compile("^[0-9a-zA-Z]{1,16}$");

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
        if (!SHORT_KEY_PATTERN.matcher(shortKey).matches()) {
            throw new ShortUrlNotFoundException(shortKey);
        }
        return repository.findByShortKey(shortKey)
                .map(ShortUrl::getOriginalUrl)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortKey));
    }
}
