package com.example.hellospring.exrate;

import com.example.hellospring.payment.ExRateProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class CachedExRateProvider implements ExRateProvider {
    private Map<String, BigDecimal> cachedExRate = new HashMap<>();
    private LocalDateTime cacheExpiryTime;

    private final ExRateProvider target;

    public CachedExRateProvider(ExRateProvider target) {
        this.target = target;
    }

    @Override
    public BigDecimal getExRate(String currency) {
        if (!cachedExRate.containsKey(currency) || cacheExpiryTime.isBefore(LocalDateTime.now())) {
            BigDecimal exRate = target.getExRate(currency);
            cachedExRate.put(currency, exRate);
            cacheExpiryTime = LocalDateTime.now().plusSeconds(3);
            System.out.println("Cache Updated");
            return exRate;
        }

        return cachedExRate.get(currency);
    }
}
