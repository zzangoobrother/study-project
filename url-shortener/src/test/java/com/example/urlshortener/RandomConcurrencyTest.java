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
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// 키 길이를 2로 줄여(62^2 = 3844 가지) 충돌과 재시도 소진이 실제로 일어나게 만든다
@TestPropertySource(properties = {"shortener.key-strategy=random", "shortener.random.key-length=2"})
class RandomConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 32;
    private static final int REQUESTS = 300;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ShortUrlRepository repository;

    // 201 응답으로 받은 키만 기록해 정리 대상으로 삼는다
    private final List<String> createdKeys = new ArrayList<>();

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
        try {
            for (int i = 0; i < REQUESTS; i++) {
                int index = i;
                Callable<ResponseEntity<String>> task = () -> {
                    // 스레드 풀 워커마다 한 번만 맞추면 충분하므로 첫 THREADS 개 작업만 대기한다
                    if (index < THREADS) {
                        ready.countDown();
                        start.await();
                    }
                    return postUrl(index);
                };
                futures.add(executor.submit(task));
            }
            ready.await();
            start.countDown();

            List<String> keys = new ArrayList<>();
            List<Integer> unexpected = new ArrayList<>();
            int created = 0;
            int unavailable = 0;
            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get();
                int status = response.getStatusCode().value();
                if (status == 201) {
                    created++;
                    keys.add(JsonPath.read(response.getBody(), "$.shortKey"));
                } else if (status == 503) {
                    unavailable++;
                } else {
                    unexpected.add(status);
                }
            }
            createdKeys.addAll(keys);
            System.out.println("[RandomConcurrencyTest] 201=" + created + ", 503=" + unavailable);

            assertThat(unexpected).as("201/503 외 상태").isEmpty();
            assertThat(keys).allSatisfy(key -> assertThat(key).hasSize(2));
            assertThat(keys).doesNotHaveDuplicates();
            long rows = keys.stream().filter(key -> repository.findByShortKey(key).isPresent()).count();
            assertThat(rows).isEqualTo(created);
        } finally {
            executor.shutdownNow();
        }
    }
}
