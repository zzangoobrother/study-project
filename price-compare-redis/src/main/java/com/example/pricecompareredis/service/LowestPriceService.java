package com.example.pricecompareredis.service;

import com.example.pricecompareredis.vo.Keyword;
import com.example.pricecompareredis.vo.Product;
import com.example.pricecompareredis.vo.ProductGroup;

import java.util.Set;

public interface LowestPriceService {

    Set getZSetValue(String key);

    int setNewProduct(Product newProduct);

    int setNewProductGroup(ProductGroup productGroup);

    int setNewProductGroupToKeyword(String keyword, String productGroupId, double score);

    Keyword getLowestPriceProductByKeyword(String keyword);
}
