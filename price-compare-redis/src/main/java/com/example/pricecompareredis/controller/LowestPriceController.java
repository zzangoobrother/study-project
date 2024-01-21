package com.example.pricecompareredis.controller;

import com.example.pricecompareredis.service.LowestPriceService;
import com.example.pricecompareredis.vo.Product;
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

    @PostMapping("/products")
    public int setNewProduct(@RequestBody Product newProduct) {
        return lowestPriceService.setNewProduct(newProduct);
    }
}
