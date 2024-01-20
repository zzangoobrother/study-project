package com.example.pricecompareredis.vo;

import lombok.Getter;

import java.util.List;

@Getter
public class Keyword {

    private String keyword;
    private List<ProductGroup> productGroups;
}
