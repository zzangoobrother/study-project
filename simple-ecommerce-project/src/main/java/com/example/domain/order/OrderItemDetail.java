package com.example.domain.order;

import com.example.enums.DeliveryType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class OrderItemDetail {
    private Long orderId;
    private Long orderItemId;
    private Long productId;
    private String productName;
    private BigDecimal productPrice;
    private String productImageUrl;
    private int quantity;
    private DeliveryType deliveryType;

    public OrderItemDetail(Long orderId, Long orderItemId, Long productId, String productName, BigDecimal productPrice, String productImageUrl, int quantity, DeliveryType deliveryType) {
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.productImageUrl = productImageUrl;
        this.quantity = quantity;
        this.deliveryType = deliveryType;
    }
}
