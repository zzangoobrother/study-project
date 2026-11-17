package com.example.urlshortener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * UNIQUE 위반 뒤의 재조회를 독립 트랜잭션에서 실행한다.
 *
 * <p>왜 REQUIRES_NEW 인가: 호출자에 REPEATABLE READ 트랜잭션이 있으면 일반 SELECT 는 처음 읽은 시점의
 * 스냅샷을 본다. 그런데 UNIQUE 검사는 스냅샷이 아니라 최신 커밋을 본다. 이 모순 때문에 다른 요청이 커밋한
 * 같은 URL 행이 INSERT 는 막으면서 재조회에는 안 보이는 일이 생긴다. 새 트랜잭션은 바깥 스냅샷과 무관하게
 * 최신 커밋을 읽으므로 이 모순을 피한다.
 *
 * <p>왜 별도 빈인가: @Transactional 은 프록시로 동작하므로, Md5ShortUrlCreator 안의 내부 메서드로 옮기면
 * (자기 호출) 프록시를 거치지 않아 애너테이션이 조용히 무시된다. 그러므로 이 클래스를 합치지 말 것.
 * Md5ShortUrlCreatorTest.raceOnSameUrlInsideOuterTransactionReturnsSameKey 가 이를 검증한다.
 */
@Component
class ShortUrlFreshReader {

    private final ShortUrlRepository repository;

    ShortUrlFreshReader(ShortUrlRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<ShortUrl> findByShortKey(String shortKey) {
        return repository.findByShortKey(shortKey);
    }
}
