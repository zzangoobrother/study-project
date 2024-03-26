package com.example.controller.category;

import com.example.controller.cart.CartModelAttributeKeys;
import com.example.controller.dto.product.CategoryProductDTO;
import com.example.domain.customer.CustomerDetail;
import com.example.service.cart.CartService;
import com.example.service.category.CategoryProductService;
import com.example.service.category.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
public class CategoryProductController {
    private static final int ITEM_PER_ROW = 3;

    private final CategoryProductService categoryProductService;
    private final CategoryService categoryService;
    private final CartService cartService;

    public CategoryProductController(CategoryProductService categoryProductService, CategoryService categoryService, CartService cartService) {
        this.categoryProductService = categoryProductService;
        this.categoryService = categoryService;
        this.cartService = cartService;
    }

    @GetMapping(value = "/category/products")
    public String list(
            @AuthenticationPrincipal CustomerDetail customerDetail,
            @RequestParam(value = "categoryId") Long categoryId,
            @PageableDefault Pageable pageable,
            Model model
    ) {
        log.info(">>> Category별 상품 조회, {}", categoryId);

        // 장바구니 개수
        if (Objects.nonNull(customerDetail)) {
            int countCartProduct = cartService.countCartProduct(customerDetail.getCustomer().getCustomerId());
            model.addAttribute(CartModelAttributeKeys.CART_ITEM_COUNT_KEY, countCartProduct);
        }

        List<CategoryProductDTO> categoryProducts = this.categoryProductService.findAllBy(categoryId, pageable);
        List<List<CategoryProductDTO>> categoryProductPartition = ListUtils.partition(categoryProducts, ITEM_PER_ROW);

        String categoryName = this.categoryService.getName(categoryId);

        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categoryName", categoryName);
        model.addAttribute("categoryProductPartition", categoryProductPartition);
        log.info(">>> Category별 상품 조회 결과, {}", categoryProducts);

        return "/category/products";
    }
}
