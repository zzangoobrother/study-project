package com.example.urlshortener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * key_range 행을 비관적 락으로 읽어 번호 범위를 한 블록씩 나눠 준다.
 *
 * <p>왜 REQUIRES_NEW 인가: 호출자가 롤백해도 할당은 남아야 한다.
 * 할당이 롤백되면 다른 서버가 같은 범위를 받아 중복 발급이 생긴다.
 * (쓰이지 않은 번호가 버려지는 것은 허용한다. 번호가 겹치는 것만 막으면 된다.)
 *
 * <p>왜 별도 빈인가: @Transactional 은 프록시로 동작하므로 자기 호출이면 프록시를 거치지 않아
 * REQUIRES_NEW 가 조용히 무시된다. 이 로직을 호출하는 쪽 클래스 안으로 합치지 말 것.
 *
 * <p>왜 Redis INCRBY 가 아닌가: 장애 후 복구 시 값이 뒤로 가면 이미 발급한 번호를 다시 발급하게 된다.
 * DB 행은 커밋된 값만 남으므로 뒤로 가지 않는다.
 */
@Component
class KeyRangeAllocator {

    private final KeyRangeRepository repository;
    private final int blockSize;

    KeyRangeAllocator(KeyRangeRepository repository,
                      @Value("${shortener.range.block-size:10000}") int blockSize) {
        if (blockSize < 1) {
            throw new IllegalArgumentException("blockSize 는 1 이상이어야 합니다: " + blockSize);
        }
        this.repository = repository;
        this.blockSize = blockSize;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NumberRange allocate() {
        KeyRange row = repository.findForUpdate(KeyRange.SHORT_URL)
                .orElseThrow(() -> new IllegalStateException(
                        "key_range 행이 없습니다: " + KeyRange.SHORT_URL));

        long start = row.getNextStart();
        long space = FeistelPermutation.SHORT_KEY_SPACE;
        if (start >= space) {
            throw ShortKeyGenerationException.exhausted();
        }
        // 마지막 블록은 번호 공간 끝에서 자른다
        long end = Math.min(start + blockSize, space);
        row.advanceTo(end);
        return new NumberRange(start, end);
    }
}
