package com.example.controller;

import com.example.controller.cart.CartModelAttributeKeys;
import com.example.controller.dto.home.HomeDisplayDTO;
import com.example.domain.customer.CustomerDetail;
import com.example.service.HomeDisplayService;
import com.example.service.cart.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Objects;

@Slf4j
@Controller
public class HomeController {

    private final HomeDisplayService homeDisplayService;
    private final CartService cartService;

    public HomeController(HomeDisplayService homeDisplayService, CartService cartService) {
        this.homeDisplayService = homeDisplayService;
        this.cartService = cartService;
    }

    @GetMapping(value = {"/", "/index", ""})
    public String index(@AuthenticationPrincipal CustomerDetail customerDetail, Model model) {

        log.info(">>> Login Customer, {}", customerDetail);
        model.addAttribute("customerDetail", customerDetail);
        if (Objects.nonNull(customerDetail)) {
            int countCartProduct = cartService.countCartProduct(customerDetail.getCustomer().getCustomerId());
            model.addAttribute(CartModelAttributeKeys.CART_ITEM_COUNT_KEY, countCartProduct);
        }

        HomeDisplayDTO homeDisplayDTO = homeDisplayService.displayHome();

        model.addAttribute("main_image_banner", homeDisplayDTO.imageBannerSrc());
        model.addAttribute("recommend_product_partitions", homeDisplayDTO.recommendProductDTOs());

        log.info("Main Image Banner={}, Recommend Products={}", homeDisplayDTO.imageBannerSrc(), homeDisplayDTO.recommendProductDTOs());

        return "/index";
    }
}
