package com.example.urlshortener;

/** Redis(L2) 조회 결과. Redis 오류는 {@link Miss} 로 표현한다. */
sealed interface RedisCacheResult {

    record Found(String url) implements RedisCacheResult {}

    record NotFound() implements RedisCacheResult {}

    record Miss() implements RedisCacheResult {}
}
