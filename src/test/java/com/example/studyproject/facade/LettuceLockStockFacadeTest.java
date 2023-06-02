package com.example.studyproject.facade;

import com.example.studyproject.domain.Stock;
import com.example.studyproject.repository.StockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class LettuceLockStockFacadeTest {

    @Autowired
    private LettuceLockStockFacade lettuceLockStockFacade;

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
    void 동시에_100개_요청() throws InterruptedException {
        int threadCoount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(33);
        CountDownLatch countDownLatch = new CountDownLatch(threadCoount);

        for(int i = 0; i < threadCoount; i++) {
            executorService.submit(() -> {
                try {
                    lettuceLockStockFacade.decrease(stockId, 1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
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
