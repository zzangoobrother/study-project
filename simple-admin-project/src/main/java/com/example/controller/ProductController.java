package com.example.controller;

import com.example.domain.product.ProductDetailView;
import com.example.service.ProductService;
import com.example.service.dto.ProductDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/products")
    public String index(Model model) {
        List<ProductDTO> productDTOS = productService.findAll();
        model.addAttribute("products", productDTOS);
        return "/products/products";
    }

    @GetMapping("/products/product-detail")
    public String detail(@RequestParam Long productId, Model model) {
        ProductDetailView productDetailView = productService.getProductDetail(productId);
        model.addAttribute("productDetail", productDetailView);
        return "/products/product-detail";
    }
}
