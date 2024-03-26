package com.example.repository.cart;

import com.example.domain.cart.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query(
            value = "SELECT COUNT(ci) " +
                    "FROM CartItem ci " +
                    "WHERE ci.cartId = :cartId AND ci.isDeleted = false "
    )
    int countCartProduct(Long cartId);
}
