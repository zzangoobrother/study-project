package com.example.domain.cart;

import com.example.enums.DeliveryType;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class CartItemProduct {
    private Long cartId;
    private Long cartItemId;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private int quantity;
    private DeliveryType deliveryType;

    public CartItemProduct(Long cartId, Long cartItemId, Long productId, String productName, BigDecimal productPrice, String productImageUrl, int quantity, DeliveryType deliveryType) {
        this.cartId = cartId;
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.productImageUrl = productImageUrl;
        this.quantity = quantity;
        this.deliveryType = deliveryType;
    }
}
