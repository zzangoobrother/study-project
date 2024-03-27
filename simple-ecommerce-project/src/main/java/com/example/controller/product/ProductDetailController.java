package com.example.controller.product;

import com.example.controller.cart.CartModelAttributeKeys;
import com.example.controller.dto.product.ProductDTO;
import com.example.domain.customer.CustomerDetail;
import com.example.domain.product.Product;
import com.example.exception.NotFoundProductException;
import com.example.service.cart.CartService;
import com.example.service.product.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Controller
public class ProductDetailController {

    private final ProductService productService;
    private final CartService cartService;

    public ProductDetailController(ProductService productService, CartService cartService) {
        this.productService = productService;
        this.cartService = cartService;
    }

    @GetMapping(value = "/product/detail")
    public String detail(@AuthenticationPrincipal CustomerDetail customerDetail, @RequestParam(name = "productId") Long productId, Model model) {
        log.info("Product detail page, {}", productId);

        model.addAttribute(CartModelAttributeKeys.CART_ITEM_COUNT_KEY, 0);
        if (Objects.nonNull(customerDetail)) {
            int countCartProduct = cartService.countCartProduct(customerDetail.getCustomer().getCustomerId());
            model.addAttribute(CartModelAttributeKeys.CART_ITEM_COUNT_KEY, countCartProduct);
        }

        Optional<Product> optionalProduct = productService.findById(productId);
        if (optionalProduct.isEmpty()) {
            throw new NotFoundProductException("상품 정보를 찾을 수 없습니다.");
        }
        Product product = optionalProduct.get();

        model.addAttribute("product", ProductDTO.of(product));

        return "/product/detail";
    }
}
