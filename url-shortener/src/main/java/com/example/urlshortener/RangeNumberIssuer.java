package com.example.urlshortener;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 받아 둔 번호 범위 [cursor, end) 에서 번호를 하나씩 메모리로 발급한다.
 * 범위를 다 쓰면 {@link KeyRangeAllocator} 에서 다음 블록을 받아 온다.
 *
 * <p>범위는 메모리에만 있으므로 프로세스가 죽으면 남은 번호는 버려진다.
 * 이어 쓰려면 발급할 때마다 위치를 저장해야 하는데, 그 저장이 늦거나 유실되면 중복 발급이 생기므로
 * 일부러 버린다. 낭비는 서버 1,000대가 매일 재시작해도 10년에 약 255억 개(공간의 0.7%)다.
 *
 * <p>할당이 실패하면 예외를 그대로 전파하고 상태는 바꾸지 않는다. 다음 호출에서 다시 할당을 시도한다.
 */
@Component
@ConditionalOnProperty(name = "shortener.key-strategy", havingValue = "range")
class RangeNumberIssuer {

    private final KeyRangeAllocator allocator;

    // 초기값은 빈 범위(cursor >= end)라 첫 호출에서 할당한다
    private long cursor = 0;
    private long end = 0;

    RangeNumberIssuer(KeyRangeAllocator allocator) {
        this.allocator = allocator;
    }

    synchronized long next() {
        if (cursor >= end) {
            NumberRange range = allocator.allocate();
            cursor = range.start();
            end = range.end();
        }
        return cursor++;
    }
}
