package com.example.urlshortener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * URL 의 MD5 해시로 키를 만든다. 같은 URL 은 같은 키를 돌려주고(중복 제거),
 * 다른 URL 과 키가 겹치면(해시 충돌) salt n 을 올려 다음 키로 넘어간다.
 */
@Component
@ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "md5")
class Md5ShortUrlCreator implements ShortUrlCreator {

    private static final Logger log = LoggerFactory.getLogger(Md5ShortUrlCreator.class);

    private final ShortUrlRepository repository;
    private final Md5KeyDeriver deriver;
    private final ShortUrlInserter inserter;
    private final int maxAttempts;

    Md5ShortUrlCreator(ShortUrlRepository repository,
                       Md5KeyDeriver deriver,
                       ShortUrlInserter inserter,
                       @Value("${shortener.md5.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
        this.repository = repository;
        this.deriver = deriver;
        this.inserter = inserter;
        this.maxAttempts = maxAttempts;
    }

    // create 에 @Transactional 을 붙이지 않는 이유는 RandomShortUrlCreator 와 같다.
    // INSERT 는 ShortUrlInserter 의 REQUIRES_NEW 에서 실행하고, UNIQUE 위반은 insert 호출 바깥에서 잡는다.
    @Override
    public String create(String originalUrl) {
        int collisions = 0;
        for (int n = 0; n < maxAttempts; n++) {
            String key = deriver.keyOf(originalUrl, n);
            Optional<ShortUrl> existing = repository.findByShortKey(key);
            if (existing.isPresent()) {
                // 같은 URL 이면 중복 제거: 기존 키를 그대로 돌려준다 (대소문자까지 정확히 비교)
                if (existing.get().getOriginalUrl().equals(originalUrl)) {
                    return key;
                }
                // 다른 URL 이 같은 키를 쓰고 있다: 해시 충돌
                collisions++;
                continue;
            }
            try {
                inserter.insert(ShortUrl.create(originalUrl, key));
                return key;
            } catch (DataIntegrityViolationException e) {
                // 조회와 INSERT 사이에 다른 요청이 같은 키를 먼저 넣었다. 같은 키로 한 번만 다시 조회한다.
                Optional<ShortUrl> again = repository.findByShortKey(key);
                if (again.isPresent() && again.get().getOriginalUrl().equals(originalUrl)) {
                    return key;
                }
                collisions++;
            }
        }
        log.warn("MD5 키 충돌 체인 소진: 시도 {}회, 충돌 {}회", maxAttempts, collisions);
        throw new ShortKeyGenerationException(maxAttempts);
    }
}
