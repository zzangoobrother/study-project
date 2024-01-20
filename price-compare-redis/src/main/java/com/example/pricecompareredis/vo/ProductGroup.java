package com.example.pricecompareredis.vo;

import lombok.Getter;

import java.util.List;

@Getter
public class ProductGroup {
    private String productGroupId;
    private List<Product> products;
}
