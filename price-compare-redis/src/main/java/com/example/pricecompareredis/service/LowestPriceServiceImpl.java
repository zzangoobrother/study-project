package com.example.pricecompareredis.service;

import com.example.pricecompareredis.vo.Product;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class LowestPriceServiceImpl implements LowestPriceService {

    private final RedisTemplate myProdPriceRedis;

    public LowestPriceServiceImpl(RedisTemplate myProdPriceRedis) {
        this.myProdPriceRedis = myProdPriceRedis;
    }

    @Override
    public Set getZSetValue(String key) {
        Set myTempSet;
        myTempSet = myProdPriceRedis.opsForZSet().rangeWithScores(key, 0, 9);
        return myTempSet;
    }

    @Override
    public int setNewProduct(Product newProduct) {
        int rank;
        myProdPriceRedis.opsForZSet().add(newProduct, newProduct.getProductGroupId(), newProduct.getPrice());
        rank = myProdPriceRedis.opsForZSet().rank(newProduct, newProduct.getProductGroupId()).intValue();
        return rank;
    }
}
