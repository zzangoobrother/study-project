package com.example.concurrencystudy.service;

import com.example.concurrencystudy.domain.Stock;
import com.example.concurrencystudy.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private PerssimisticLockStockService perssimisticLockStockService;

    @Autowired
    private StockRepository stockRepository;

    private Long stockId;
    private Stock stock;

    @BeforeEach
    void setUp() {
        stock = new Stock(1L, 100L);
        stockRepository.save(stock);

        stockId = stock.getId();
    }

    @AfterEach
    void after() {
        stockRepository.deleteAll();
    }

    @Test
    void stock_decrease() {
        stockService.decrease(stockId, 1L);

        Stock stock = stockRepository.findById(stockId).orElseThrow();

        assertEquals(99, stock.getQuantity());
    }

    @Test
    void 동시에_100개_요청() throws InterruptedException {
        int threadCoount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(33);
        CountDownLatch countDownLatch = new CountDownLatch(threadCoount);

        for(int i = 0; i < threadCoount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decrease(stockId, 1L);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();

        Stock stock = stockRepository.findById(stockId).orElseThrow();

        assertEquals(0L, stock.getQuantity());
    }

    /*
        @Transactional의 동작 방식 때문에 테스트 실패
        @Transactional은 aop 방식으로 autoCommit을 false 후
        service 로직 실행, 그리고 commit을 하는데
        이때 commit을 하는 시점과 service를 마치고 synchronized 메소드를
        빠져나오는 시점이 달라서 다른 client가 변경전 데이터를 조회하기 때문에
        발생합니다.
        만약 @Transactional을 삭제하고 실행한다면 테스트는 성공합니다.
    */
    @Test
    void 동시에_100개_요청_synchronized() throws InterruptedException {
        int threadCoount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(33);
        CountDownLatch countDownLatch = new CountDownLatch(threadCoount);

        for(int i = 0; i < threadCoount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.synchronizedDecrease(stockId, 1L);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();

        Stock stock = stockRepository.findById(stockId).orElseThrow();

        assertEquals(0L, stock.getQuantity());
    }

    @Test
    void 동시에_100개_요청_비관적락() throws InterruptedException {
        int threadCoount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(33);
        CountDownLatch countDownLatch = new CountDownLatch(threadCoount);

        for(int i = 0; i < threadCoount; i++) {
            executorService.submit(() -> {
                try {
                    perssimisticLockStockService.decreas(stockId, 1L);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();

        Stock stock = stockRepository.findById(stockId).orElseThrow();

        assertEquals(0L, stock.getQuantity());
    }
}
