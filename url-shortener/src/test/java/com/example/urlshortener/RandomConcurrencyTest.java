package com.example.urlshortener;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 키 길이를 2로 줄여(62^2 = 3844 가지) 경쟁 조건 하에서 UNIQUE 충돌이 실제로 일어나게 만든다.
// 이 부하(300건)에서 재시도 소진(503)은 정상 경로가 아니므로 0 건이어야 한다
@TestPropertySource(properties = {"shortener.key-strategy=random", "shortener.random.key-length=2"})
class RandomConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 32;
    private static final int REQUESTS = 300;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ShortUrlRepository repository;

    // 201 응답으로 받은 키만 기록해 정리 대상으로 삼는다
    private final List<String> createdKeys = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanUp() {
        // counter 전략이 만든 길이 2 키와 충돌하지 않도록 이 테스트가 만든 행만 지운다
        createdKeys.forEach(key -> repository.findByShortKey(key).ifPresent(repository::delete));
    }

    private ResponseEntity<String> postUrl(int index) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"url\":\"https://example.com/concurrent/" + index + "\"}";
        return restTemplate.postForEntity("/api/urls", new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void concurrentRequestsNeverShareAKey() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        int created = 0;
        try {
            for (int i = 0; i < REQUESTS; i++) {
                int index = i;
                Callable<ResponseEntity<String>> task = () -> {
                    // 첫 THREADS 개 작업만 시작 신호를 기다려 동시에 출발시킨다
                    if (index < THREADS) {
                        ready.countDown();
                        start.await();
                    }
                    ResponseEntity<String> response = postUrl(index);
                    // 201 키는 받는 즉시 기록해 이후 예외가 나도 정리 대상에서 빠지지 않게 한다
                    if (response.getStatusCode().value() == 201) {
                        createdKeys.add(JsonPath.read(response.getBody(), "$.shortKey"));
                    }
                    return response;
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("모든 스레드 준비").isTrue();
            start.countDown();

            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get(60, TimeUnit.SECONDS);
                int status = response.getStatusCode().value();
                if (status == 201) {
                    created++;
                } else if (status == 503) {
                    unavailable.add(response.getBody());
                } else {
                    unexpected.add(status + " " + response.getBody());
                }
            }
        } finally {
            // 진행 중인 요청이 끝날 때까지 기다려, cleanUp 이후에 행이 생기지 않게 한다
            start.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }

        assertThat(unexpected).as("201/503 외 상태").isEmpty();
        assertThat(unavailable).as("이 부하에서 503 은 정상 경로가 아니다").isEmpty();
        assertThat(createdKeys).hasSize(created);
        assertThat(createdKeys).allSatisfy(key -> assertThat(key).hasSize(2));
        assertThat(createdKeys).doesNotHaveDuplicates();
        long rows = createdKeys.stream().filter(key -> repository.findByShortKey(key).isPresent()).count();
        assertThat(rows).isEqualTo(created);
    }
}
