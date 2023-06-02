package com.example.studyproject.service;

import com.example.studyproject.domain.Stock;
import com.example.studyproject.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerssimisticLockStockService {

    private final StockRepository stockRepository;

    public PerssimisticLockStockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void decreas(Long id, Long quantity) {
        Stock stock = stockRepository.findByWithPerssimisticLock(id);

        stock.decrease(quantity);

        stockRepository.saveAndFlush(stock);
    }
}
