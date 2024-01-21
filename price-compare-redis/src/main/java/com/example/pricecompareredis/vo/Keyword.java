package com.example.pricecompareredis.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Keyword {

    private String keyword;
    private List<ProductGroup> productGroups;
}
