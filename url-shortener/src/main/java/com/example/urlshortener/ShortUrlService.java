package com.example.urlshortener;

import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ShortUrlService {

    // short_key 컬럼은 ascii 라 비 ASCII 조회값은 DB 에서 collation 오류가 난다. 조회 전에 형식을 걸러낸다
    private static final Pattern SHORT_KEY_PATTERN = Pattern.compile("^[0-9a-zA-Z]{1,16}$");

    private final ShortUrlCreator shortUrlCreator;
    private final ShortUrlLookup lookup;

    public ShortUrlService(ShortUrlCreator shortUrlCreator, ShortUrlLookup lookup) {
        this.shortUrlCreator = shortUrlCreator;
        this.lookup = lookup;
    }

    // 트랜잭션 경계는 구현체(ShortUrlCreator)가 소유한다. 생성이 성공한 뒤에만 L2 에 기록한다
    public String shorten(String originalUrl) {
        String shortKey = shortUrlCreator.create(originalUrl);
        lookup.recordCreated(shortKey, originalUrl);
        return shortKey;
    }

    // 캐시에 맞으면 DB 커넥션을 열지 않으려고 @Transactional 을 두지 않는다.
    // DB 조회가 필요하면 Spring Data 가 자체 읽기 트랜잭션으로 실행한다
    public String getOriginalUrl(String shortKey) {
        if (!SHORT_KEY_PATTERN.matcher(shortKey).matches()) {
            throw new ShortUrlNotFoundException(shortKey);
        }
        return lookup.findOriginalUrl(shortKey)
                .orElseThrow(() -> new ShortUrlNotFoundException(shortKey));
    }
}
