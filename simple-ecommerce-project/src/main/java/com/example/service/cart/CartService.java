package com.example.service.cart;

import com.example.domain.cart.Cart;
import com.example.exception.NotFoundCartException;
import com.example.repository.cart.CartItemRepository;
import com.example.repository.cart.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Transactional
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public int countCartProduct(Long customerId) {
        Optional<Cart> optionalCart = getCartByCustomerId(customerId);
        if (optionalCart.isEmpty()) {
            return 0;
        }
        Cart cart = optionalCart.get();
        int cartProductCount = this.cartItemRepository.countCartProduct(cart.getCartId());
        log.info(">>> Cart Item Count = {}", cartProductCount);
        return cartProductCount;
    }

    public Optional<Cart> getCartByCustomerId(Long customerId) {
        Optional<Cart> optionalCart = this.cartRepository.findByCustomerIdAndIsDeletedIsFalse(customerId);
        if (optionalCart.isEmpty()) {
            throw new NotFoundCartException("고객의 장바구니를 찾을 수 없습니다.");
        }
        return optionalCart;
    }
}
