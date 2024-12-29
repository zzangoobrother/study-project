package com.example.controller;

import com.example.cassandra.entity.Product;
import com.example.dto.DecreaseStockCountDto;
import com.example.dto.RegisterProductDto;
import com.example.service.CatalogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping("/catalog/products")
    public Product registerProduct(@RequestBody RegisterProductDto dto) {
        return catalogService.registerProduct(dto.sellerId(), dto.name(), dto.description(), dto.price(), dto.stockCount(), dto.tags());
    }

    @DeleteMapping("/catalog/products/{productId}")
    public void deleteProduct(@PathVariable Long productId) {
        catalogService.deleteProduct(productId);
    }

    @GetMapping("/catalog/products/{productId}")
    public Product getProductById(@PathVariable Long productId) {
        return catalogService.getProductById(productId);
    }

    @GetMapping("/catalog/products/{sellerId}/products")
    public List<Product> getProductBySellerId(@PathVariable Long sellerId) {
        return catalogService.getProductsBySellerId(sellerId);
    }

    @PostMapping("/catalog/products/{productId}/decreaseStockCount")
    public Product getProductBySellerId(@PathVariable Long productId, @RequestBody DecreaseStockCountDto dto) {
        return catalogService.decreaseStockCount(productId, dto.decreaseCount());
    }
}
