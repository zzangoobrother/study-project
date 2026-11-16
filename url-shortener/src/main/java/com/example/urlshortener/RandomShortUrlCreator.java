package com.example.urlshortener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "random", matchIfMissing = true)
class RandomShortUrlCreator implements ShortUrlCreator {

    private final ShortUrlRepository repository;
    private final RandomKeyGenerator keyGenerator;
    private final TransactionTemplate attemptTx;
    private final int maxAttempts;

    RandomShortUrlCreator(ShortUrlRepository repository,
                          RandomKeyGenerator keyGenerator,
                          PlatformTransactionManager txManager,
                          @Value("${shortener.random.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.keyGenerator = keyGenerator;
        this.attemptTx = new TransactionTemplate(txManager);
        // 호출자에 트랜잭션이 있어도 시도마다 독립된 새 트랜잭션에서 INSERT 한다
        this.attemptTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.maxAttempts = maxAttempts;
    }

    // 의도적으로 create 에 @Transactional 을 붙이지 않는다.
    // 루프 전체를 하나의 트랜잭션으로 묶으면 UNIQUE 위반 시 트랜잭션이 rollback-only 로 표시되어
    // 재시도에 성공해도 커밋 시점에 UnexpectedRollbackException 이 발생한다.
    // 시도 1회의 INSERT 는 REQUIRES_NEW 템플릿 안에서 실행하므로, 호출자에 바깥 트랜잭션이 있어도
    // 실패한 시도만 롤백되고 바깥 트랜잭션은 오염되지 않는다.
    // DataIntegrityViolationException 은 템플릿 바깥에서 잡아야 한다 (안에서 잡으면 이미 rollback-only 가 된 뒤다).
    @Override
    public String create(String originalUrl) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String key = keyGenerator.generate();
            // 사전 확인: 이미 있는 키면 INSERT 없이 다음 시도로 넘어간다
            if (repository.existsByShortKey(key)) {
                continue;
            }
            try {
                // 시도마다 새 엔티티를 만든다 (실패한 엔티티를 재사용하지 않는다)
                attemptTx.executeWithoutResult(
                        status -> repository.saveAndFlush(ShortUrl.create(originalUrl, key)));
                return key;
            } catch (DataIntegrityViolationException e) {
                // 사전 확인과 INSERT 사이에 다른 요청이 같은 키를 넣은 경우 (UNIQUE 위반)
            }
        }
        throw new ShortKeyGenerationException(maxAttempts);
    }
}
