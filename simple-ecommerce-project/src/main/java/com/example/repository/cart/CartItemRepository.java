package com.example.repository.cart;

import com.example.domain.cart.CartItem;
import com.example.domain.cart.CartItemProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query(
            value = "SELECT COUNT(ci) " +
                    "FROM CartItem ci " +
                    "WHERE ci.cartId = :cartId AND ci.isDeleted = false "
    )
    int countCartProduct(Long cartId);

    @Query(
            value = "SELECT new com.example.domain.cart.CartItemProduct(ci.cartId, ci.cartItemId, ci.productId, p.productName, p.price, p.imageUrl, ci.quantity, p.deliveryType) " +
                    "FROM CartItem ci " +
                    "INNER JOIN Product p ON ci.productId = p.id " +
                    "WHERE ci.cartId = :cartId AND ci.isDeleted = false"

    )
    List<CartItemProduct> findAllCartItems(@Param("cartId") Long cartId);

    @Query(
            value = "SELECT new com.example.domain.cart.CartItemProduct(ci.cartId, ci.cartItemId, ci.productId, p.productName, p.price, p.imageUrl, ci.quantity, p.deliveryType) " +
                    "FROM CartItem ci " +
                    "INNER JOIN Product p ON ci.productId = p.id " +
                    "WHERE ci.cartItemId IN (:cartItemIds) AND ci.isDeleted = false"

    )
    List<CartItemProduct> findAllCartItems(@Param("cartItemIds") List<Long> cartItemIds);

    List<CartItem> findAllByCartIdAndIsDeletedIsFalse(Long cartId);
}
