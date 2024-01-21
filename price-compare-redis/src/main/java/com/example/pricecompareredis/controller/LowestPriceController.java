package com.example.pricecompareredis.controller;

import com.example.pricecompareredis.service.LowestPriceService;
import com.example.pricecompareredis.vo.Keyword;
import com.example.pricecompareredis.vo.Product;
import com.example.pricecompareredis.vo.ProductGroup;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RequestMapping
@RestController
public class LowestPriceController {

    private final LowestPriceService lowestPriceService;

    public LowestPriceController(LowestPriceService lowestPriceService) {
        this.lowestPriceService = lowestPriceService;
    }

    @GetMapping("/getZSETValue")
    public Set getZSetValue (String key) {
        return lowestPriceService.getZSetValue(key);
    }

    @PutMapping("/products")
    public int setNewProduct(@RequestBody Product newProduct) {
        return lowestPriceService.setNewProduct(newProduct);
    }

    @PutMapping("/productGroup")
    public int setNewProductGroup(@RequestBody ProductGroup productGroup) {
        return lowestPriceService.setNewProductGroup(productGroup);
    }

    @PutMapping("/productGroupToKeyword")
    public int setNewProductGroupToKeyword(String keyword, String productGroupId, double score) {
        return lowestPriceService.setNewProductGroupToKeyword(keyword, productGroupId, score);
    }

    @GetMapping("/productPrice/lowest")
    public Keyword getLowestPriceProductByKeyword(String keyword) {
        return lowestPriceService.getLowestPriceProductByKeyword(keyword);
    }
}
