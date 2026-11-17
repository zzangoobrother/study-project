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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 같은 새 URL 을 16개 스레드가 동시에 보내도 키와 행이 하나뿐이어야 한다.
// 모든 스레드가 같은 키로 몰리므로 UNIQUE 위반 경로(재조회 후 같은 키 반환)가 거의 매번 실행된다.
@TestPropertySource(properties = {"shortener.key-strategy=md5", "shortener.md5.key-length=3"})
class Md5ConcurrencyTest extends IntegrationTestSupport {

    private static final int THREADS = 16;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ShortUrlRepository repository;

    @Autowired
    Md5KeyDeriver deriver;

    private String url;

    @AfterEach
    void cleanUp() {
        // 이 테스트가 만든 URL 의 행만 지운다
        if (url != null) {
            repository.findAll().stream()
                    .filter(s -> url.equals(s.getOriginalUrl()))
                    .forEach(repository::delete);
        }
    }

    private ResponseEntity<String> postUrl(String target) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"url\":\"" + target + "\"}";
        return restTemplate.postForEntity("/api/urls", new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void concurrentRequestsForSameNewUrlShareOneKeyAndRow() throws Exception {
        url = "https://md5test.example/concurrent/" + UUID.randomUUID();
        String expectedKey = deriver.keyOf(url, 0);
        assumeTrue(repository.findByShortKey(expectedKey).isEmpty());

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        List<String> keys = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                Callable<ResponseEntity<String>> task = () -> {
                    // 모든 스레드가 준비되면 한꺼번에 출발한다
                    ready.countDown();
                    start.await();
                    ResponseEntity<String> response = postUrl(url);
                    // 응답을 받는 즉시 키를 기록한다
                    if (response.getStatusCode().value() == 201) {
                        keys.add(JsonPath.read(response.getBody(), "$.shortKey"));
                    }
                    return response;
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("모든 스레드 준비").isTrue();
            start.countDown();

            for (Future<ResponseEntity<String>> future : futures) {
                ResponseEntity<String> response = future.get(60, TimeUnit.SECONDS);
                if (response.getStatusCode().value() != 201) {
                    unexpected.add(response.getStatusCode().value() + " " + response.getBody());
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

        assertThat(unexpected).as("201 외 상태").isEmpty();
        assertThat(keys).hasSize(THREADS);
        assertThat(keys).allSatisfy(key -> assertThat(key).isEqualTo(expectedKey));
        long rows = repository.findAll().stream().filter(s -> url.equals(s.getOriginalUrl())).count();
        assertThat(rows).isEqualTo(1);
    }
}
