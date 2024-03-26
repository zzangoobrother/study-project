package com.example.controller.cart;

import com.example.domain.cart.Cart;
import com.example.domain.cart.CartItemProduct;
import com.example.domain.customer.CustomerDetail;
import com.example.exception.NotFoundCustomerException;
import com.example.service.cart.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Controller
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping(value = {"/cart", "/cart/"})
    public String cart(@AuthenticationPrincipal CustomerDetail customerDetail, Model model) {
        log.info(">>> 장바구니 목록, 고객 정보, {}", customerDetail);
        if (Objects.isNull(customerDetail)) {
            throw new NotFoundCustomerException("고객 정보를 찾을 수 없습니다.");
        }
        Long customerId = customerDetail.getCustomer().getCustomerId();

        // 장바구니 개수
        int countCartProduct = cartService.countCartProduct(customerDetail.getCustomer().getCustomerId());
        model.addAttribute(CartModelAttributeKeys.CART_ITEM_COUNT_KEY, countCartProduct);

        Optional<Cart> optionalCart = cartService.getCartByCustomerId(customerId);
        if (optionalCart.isEmpty()) {
            Cart cart = cartService.create(customerId);
            model.addAttribute(CartModelAttributeKeys.ATTRIBUTE_NAME_CART_ID, cart.getCartId());
        } else {
            model.addAttribute(CartModelAttributeKeys.ATTRIBUTE_NAME_CART_ID, optionalCart.get().getCartId());
        }

        List<CartItemProduct> cartItemProducts = cartService.getCartItems(customerDetail.getCustomer().getCustomerId());
        model.addAttribute("cartItemProducts", cartItemProducts);

        return "/cart/cart";
    }

    @PostMapping(value = {"/cart/{productId}"})
    @ResponseBody
    public boolean add(@AuthenticationPrincipal CustomerDetail customerDetail, @PathVariable Long productId) {
        log.info(">>> 장바구니 추가, 고객 정보, {}", customerDetail);
        Long customerId = customerDetail.getCustomer().getCustomerId();
        this.cartService.add(customerId, productId);
        return true;
    }

    @DeleteMapping(value = "/cart/cart-item/{cartItemId}")
    @ResponseBody
    public Long remove(@AuthenticationPrincipal CustomerDetail customerDetail, @PathVariable Long cartItemId) {
        log.info(">>> 장바구니 제거, {}", cartItemId);
        Long customerId = customerDetail.getCustomer().getCustomerId();
        this.cartService.remove(customerId, cartItemId);
        return cartItemId;
    }
}
