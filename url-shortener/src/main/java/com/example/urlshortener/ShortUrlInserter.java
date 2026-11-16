package com.example.urlshortener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랜덤 키 시도 1회의 INSERT 를 독립 트랜잭션으로 실행한다.
 *
 * <p>왜 REQUIRES_NEW 인가: 호출자에 트랜잭션이 있어도 시도마다 독립적으로 커밋·롤백한다.
 * 한 시도의 UNIQUE 위반이 바깥 트랜잭션을 rollback-only 로 만들지 않게 하기 위해서다.
 *
 * <p>왜 별도 빈인가: @Transactional 은 프록시로 동작하므로, RandomShortUrlCreator 안의
 * private/내부 메서드로 옮기면(자기 호출) 프록시를 거치지 않아 애너테이션이 조용히 무시된다.
 * 그러므로 이 클래스를 RandomShortUrlCreator 에 합치지 말 것.
 * RandomShortUrlCreatorTest.raceCollisionRetriesEvenInsideOuterTransaction 이 이를 검증한다.
 */
@Component
class ShortUrlInserter {

    private final ShortUrlRepository repository;

    ShortUrlInserter(ShortUrlRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(ShortUrl shortUrl) {
        repository.saveAndFlush(shortUrl);
    }
}
