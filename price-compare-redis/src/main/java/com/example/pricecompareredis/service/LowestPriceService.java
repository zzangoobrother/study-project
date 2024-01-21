package com.example.pricecompareredis.service;

import com.example.pricecompareredis.vo.Product;

import java.util.Set;

public interface LowestPriceService {

    Set getZSetValue(String key);

    int setNewProduct(Product newProduct);
}
