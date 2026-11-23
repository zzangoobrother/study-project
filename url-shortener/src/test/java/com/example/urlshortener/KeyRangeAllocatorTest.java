package com.example.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestPropertySource(properties = "shortener.range.block-size=10")
class KeyRangeAllocatorTest extends IntegrationTestSupport {

    private static final int BLOCK_SIZE = 10;

    @Autowired
    KeyRangeAllocator allocator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    // 할당으로 늘어난 next_start 는 되돌리지 않는다. 이미 나눠 준 범위를 다시 나눠 주면 안 되기 때문이다.
    private long nextStart() {
        return jdbcTemplate.queryForObject(
                "SELECT next_start FROM key_range WHERE name = ?", Long.class, KeyRange.SHORT_URL);
    }

    private void setNextStart(long value) {
        jdbcTemplate.update("UPDATE key_range SET next_start = ? WHERE name = ?", value, KeyRange.SHORT_URL);
    }

    @Test
    void allocatesConsecutiveBlocks() {
        long s = nextStart();

        NumberRange first = allocator.allocate();
        NumberRange second = allocator.allocate();

        assertThat(first).isEqualTo(new NumberRange(s, s + BLOCK_SIZE));
        assertThat(second).isEqualTo(new NumberRange(s + BLOCK_SIZE, s + 2 * BLOCK_SIZE));
    }

    @Test
    void concurrentAllocationsAreDisjointAndContiguous() throws Exception {
        int threads = 16;
        int perThread = 5;
        long s = nextStart();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<List<NumberRange>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<List<NumberRange>> task = () -> {
                    ready.countDown();
                    start.await();
                    List<NumberRange> result = new ArrayList<>();
                    for (int j = 0; j < perThread; j++) {
                        result.add(allocator.allocate());
                    }
                    return result;
                };
                futures.add(pool.submit(task));
            }
            ready.await();
            start.countDown();

            List<NumberRange> all = new ArrayList<>();
            for (Future<List<NumberRange>> future : futures) {
                all.addAll(future.get());
            }
            all.sort(Comparator.comparingLong(NumberRange::start));

            assertThat(all).hasSize(threads * perThread);
            assertThat(all.get(0).start()).isEqualTo(s);
            for (int i = 0; i < all.size() - 1; i++) {
                assertThat(all.get(i).end()).isEqualTo(all.get(i + 1).start());
            }
            assertThat(all.get(all.size() - 1).end()).isEqualTo(s + (long) threads * perThread * BLOCK_SIZE);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void allocationCommitsEvenIfCallerRollsBack() {
        long s = nextStart();

        transactionTemplate.executeWithoutResult(status -> {
            allocator.allocate();
            status.setRollbackOnly();
        });

        assertThat(nextStart()).isEqualTo(s + BLOCK_SIZE);
    }

    @Test
    void clipsLastBlockAtKeySpace() {
        long original = nextStart();
        long n = FeistelPermutation.SHORT_KEY_SPACE;
        try {
            // 이 테스트만 next_start 를 줄인다. N - 3 이후 영역은 아직 나눠 준 적 없는 범위라 안전하다.
            setNextStart(n - 3);

            NumberRange last = allocator.allocate();

            assertThat(last).isEqualTo(new NumberRange(n - 3, n));
            assertThatThrownBy(() -> allocator.allocate()).isInstanceOf(ShortKeyGenerationException.class);
        } finally {
            setNextStart(original);
        }
    }

    @Test
    void missingRowFailsClearly() {
        long original = nextStart();
        try {
            jdbcTemplate.update("DELETE FROM key_range WHERE name = ?", KeyRange.SHORT_URL);

            assertThatThrownBy(() -> allocator.allocate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(KeyRange.SHORT_URL);
        } finally {
            jdbcTemplate.update("INSERT INTO key_range (name, next_start) VALUES (?, ?)",
                    KeyRange.SHORT_URL, original);
        }
    }
}
