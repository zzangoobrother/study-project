package com.example.pricecompareredis.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Product {

    private String productGroupId;
    private String productId;
    private int price;
}
