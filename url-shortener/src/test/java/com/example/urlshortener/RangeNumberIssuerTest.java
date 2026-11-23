package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "shortener.range.block-size=10")
class RangeNumberIssuerTest extends IntegrationTestSupport {

    private static final int BLOCK_SIZE = 10;

    @Autowired
    KeyRangeAllocator allocator;

    @Test
    void issuersOnManyServersNeverIssueTheSameNumber() throws Exception {
        int servers = 4;
        int threads = 16;
        int total = 2000;
        int perThread = total / threads;
        List<RangeNumberIssuer> issuers = new ArrayList<>();
        for (int i = 0; i < servers; i++) {
            issuers.add(new RangeNumberIssuer(allocator));
        }
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<List<Long>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                RangeNumberIssuer issuer = issuers.get(i % servers);
                // 2,000 / 16 = 125 로 나누어떨어진다
                int count = perThread;
                Callable<List<Long>> task = () -> {
                    ready.countDown();
                    start.await();
                    List<Long> result = new ArrayList<>();
                    for (int j = 0; j < count; j++) {
                        result.add(issuer.next());
                    }
                    return result;
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            start.countDown();

            Set<Long> numbers = new HashSet<>();
            int issued = 0;
            for (Future<List<Long>> future : futures) {
                List<Long> part = future.get();
                issued += part.size();
                numbers.addAll(part);
            }

            assertThat(issued).isEqualTo(total);
            assertThat(numbers).hasSize(total);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void restartDiscardsRestOfRange() {
        RangeNumberIssuer a = new RangeNumberIssuer(allocator);
        long a0 = a.next();
        a.next();
        a.next();
        // A 의 범위는 [a0, a0 + 10) 이고 3개만 썼다. A 를 버리면 나머지 7개는 다시 나오지 않아야 한다.

        RangeNumberIssuer b = new RangeNumberIssuer(allocator);
        long b0 = b.next();
        assertThat(b0).isGreaterThanOrEqualTo(a0 + BLOCK_SIZE);

        RangeNumberIssuer c = new RangeNumberIssuer(allocator);
        List<Long> later = new ArrayList<>();
        later.add(b0);
        for (int i = 0; i < 50; i++) {
            later.add(b.next());
        }
        for (int i = 0; i < 50; i++) {
            later.add(c.next());
        }

        assertThat(later).noneMatch(n -> n >= a0 + 3 && n < a0 + BLOCK_SIZE);
    }
}
