package com.example.pricecompareredis.controller;

import com.example.pricecompareredis.service.LowestPriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
