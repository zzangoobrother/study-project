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
    private final ShortUrlFreshReader freshReader;
    private final int maxAttempts;

    Md5ShortUrlCreator(ShortUrlRepository repository,
                       Md5KeyDeriver deriver,
                       ShortUrlInserter inserter,
                       ShortUrlFreshReader freshReader,
                       @Value("${shortener.md5.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
        this.repository = repository;
        this.deriver = deriver;
        this.inserter = inserter;
        this.freshReader = freshReader;
        this.maxAttempts = maxAttempts;
    }

    // create 에 @Transactional 을 붙이지 않는 이유는 RandomShortUrlCreator 와 같다.
    // INSERT 는 ShortUrlInserter 의 REQUIRES_NEW 에서 실행하고, UNIQUE 위반은 insert 호출 바깥에서 잡는다.
    // 첫 조회는 호출자 트랜잭션의 스냅샷을 볼 수 있지만, 그러면 INSERT 가 UNIQUE 위반을 내고
    // 그 뒤 재조회는 ShortUrlFreshReader 의 REQUIRES_NEW 로 최신 커밋을 읽어 바로잡는다.
    @Override
    public String create(String originalUrl) {
        int precheckCollisions = 0;
        int raceCollisions = 0;
        for (int n = 0; n < maxAttempts; n++) {
            String key = deriver.keyOf(originalUrl, n);
            Optional<ShortUrl> existing = repository.findByShortKey(key);
            if (existing.isPresent()) {
                // 같은 URL 이면 중복 제거: 기존 키를 그대로 돌려준다 (대소문자까지 정확히 비교)
                if (existing.get().getOriginalUrl().equals(originalUrl)) {
                    return key;
                }
                // 다른 URL 이 같은 키를 쓰고 있다: 해시 충돌
                precheckCollisions++;
                continue;
            }
            try {
                inserter.insert(ShortUrl.create(originalUrl, key));
                return key;
            } catch (DataIntegrityViolationException e) {
                // 조회와 INSERT 사이에 다른 요청이 같은 키를 먼저 넣었다. 같은 키로 한 번만 다시 조회한다.
                Optional<ShortUrl> again = freshReader.findByShortKey(key);
                if (again.isPresent() && again.get().getOriginalUrl().equals(originalUrl)) {
                    log.debug("MD5 경쟁 뒤 재조회로 같은 URL 을 찾아 회복했다: n={}", n);
                    return key;
                }
                raceCollisions++;
            }
        }
        log.warn("MD5 키 충돌 체인 소진: 시도 {}회, 사전 확인 충돌 {}회, 경쟁 충돌 {}회",
                maxAttempts, precheckCollisions, raceCollisions);
        throw new ShortKeyGenerationException(maxAttempts);
    }
}
