package com.example.urlshortener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 범위 할당 카운터 번호를 Feistel 순열로 섞어 7자리 Base62 키를 만든다.
 *
 * <p>range 키끼리는 충돌하지 않는다(번호가 유일하고 순열은 일대일 대응이다).
 * UNIQUE 위반은 같은 DB 에 다른 전략이 만든 키가 남아 있을 때만 생긴다.
 */
@Component
@ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "range")
class RangeShortUrlCreator implements ShortUrlCreator {

    private static final Logger log = LoggerFactory.getLogger(RangeShortUrlCreator.class);

    private static final int KEY_LENGTH = 7;

    private final RangeNumberIssuer issuer;
    private final ShortUrlInserter inserter;
    private final FeistelPermutation feistel;
    private final int maxAttempts;

    RangeShortUrlCreator(RangeNumberIssuer issuer,
                         ShortUrlInserter inserter,
                         @Value("${shortener.range.feistel-secret}") long feistelSecret,
                         @Value("${shortener.range.max-attempts:5}") int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive: " + maxAttempts);
        }
        this.issuer = issuer;
        this.inserter = inserter;
        this.feistel = FeistelPermutation.forShortKey(feistelSecret);
        this.maxAttempts = maxAttempts;
    }

    // 의도적으로 create 에 @Transactional 을 붙이지 않는다.
    // 루프 전체를 하나의 트랜잭션으로 묶으면 UNIQUE 위반 시 트랜잭션이 rollback-only 로 표시되어
    // 재시도에 성공해도 커밋 시점에 UnexpectedRollbackException 이 발생한다.
    // 시도 1회의 INSERT 는 ShortUrlInserter 의 REQUIRES_NEW 트랜잭션에서 실행하므로, 호출자에 바깥 트랜잭션이
    // 있어도 실패한 시도만 롤백되고 바깥 트랜잭션은 오염되지 않는다.
    // DataIntegrityViolationException 은 insert 호출 바깥에서 잡아야 한다 (안에서 잡으면 이미 rollback-only 가 된 뒤다).
    @Override
    public String create(String originalUrl) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String key = Base62.encodePadded(feistel.permute(issuer.next()), KEY_LENGTH);
            try {
                // 시도마다 새 엔티티를 만든다 (실패한 엔티티를 재사용하지 않는다)
                inserter.insert(ShortUrl.create(originalUrl, key));
                return key;
            } catch (DataIntegrityViolationException e) {
                // 다른 전략이 같은 키를 이미 썼다. 그 번호는 버리고 다음 번호로 넘어간다.
            }
        }
        log.warn("범위 키 생성 재시도 소진: 시도 {}회, 모두 UNIQUE 위반 (다른 전략이 만든 키와 충돌)", maxAttempts);
        throw new ShortKeyGenerationException(maxAttempts);
    }
}
