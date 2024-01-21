package com.example.pricecompareredis.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductGroup {
    private String productGroupId;
    private List<Product> products;
}
