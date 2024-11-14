package com.example.service;

import com.example.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserQueueService {

    @Value("${scheduler.enabled}")
    private boolean scheduling;

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private final String USER_QUEUE_WAIT_KEY = "user:queue:%s:wait";
    private final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "user:queue:*:wait";
    private final String USER_QUEUE_PROCEED_KEY = "user:queue:%s:proceed";

    // 대기열 대기 상태
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        // redis sortedset, key : userId, value : unix timestamp
        long unixTimestamp = Instant.now().getEpochSecond();
        return reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString(), unixTimestamp)
                .filter(i -> i)
                .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()))
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString()))
                .map(i -> i >= 0 ? i + 1 : i);
    }

    // 진입이 가능한 상태인지 조회
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_PROCEED_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(i -> i >= 0);
    }

    public Mono<Boolean> isAllowedByToken(final String queue, final Long userId, final String token) {
        return this.generateToken(queue, userId)
                .filter(gen -> gen.equalsIgnoreCase(token))
                .map(i -> true)
                .defaultIfEmpty(false);
    }

    // 진입을 허용
    public Mono<Long> allowUser(final String queue, final Long count) {
        // 진입을 허용하는 단계
        // 1. wait queue 사용자를 제거
        // 2. proceed queue 사용자를 추가
        return reactiveRedisTemplate.opsForZSet().popMin(USER_QUEUE_WAIT_KEY.formatted(queue), count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet().add(USER_QUEUE_PROCEED_KEY.formatted(queue), member.getValue(), Instant.now().getEpochSecond()))
                .count();
    }

    public Mono<Long> getRank(final String queue, final Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank(USER_QUEUE_WAIT_KEY.formatted(queue), userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    public Mono<String> generateToken(final String queue, final Long userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = "user-queue-%s-%d".formatted(queue, userId);
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte aByte : encodedHash) {
                hexString.append(String.format("%02x", aByte));
            }

            return Mono.just(hexString.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Scheduled(initialDelay = 5000, fixedDelay = 3000)
    public void scheduleAllowUser() {
        if (!scheduling) {
            log.info("passed schedule");
            return;
        }

        log.info("called scheduling... ");

        long maxAllowUserCount = 3L;

        // 사용자를 허용하는 코드 작성
        reactiveRedisTemplate.scan(
                ScanOptions
                .scanOptions()
                .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                .count(100)
                .build()
        )
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed)))
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
                .subscribe();
    }
}
