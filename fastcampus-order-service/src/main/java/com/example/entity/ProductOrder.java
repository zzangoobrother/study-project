package com.example.entity;

import com.example.enums.OrderStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class ProductOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long productId;

    private Long count;

    private OrderStatus orderStatus;

    private Long paymentId;

    private Long deliveryId;

    private String deliveryAddress;

    public ProductOrder(Long userId, Long productId, Long count, OrderStatus orderStatus, Long paymentId, Long deliveryId, String deliveryAddress) {
        this.userId = userId;
        this.productId = productId;
        this.count = count;
        this.orderStatus = orderStatus;
        this.paymentId = paymentId;
        this.deliveryId = deliveryId;
        this.deliveryAddress = deliveryAddress;
    }

    public void update(long paymentId, long deliveryId, OrderStatus orderStatus) {
        this.paymentId = paymentId;
        this.deliveryId = deliveryId;
        this.orderStatus = orderStatus;
    }

    public void update(long paymentId, OrderStatus orderStatus) {
        this.paymentId = paymentId;
        this.orderStatus = orderStatus;
    }

    public void update(OrderStatus orderStatus, String deliveryAddress) {
        this.orderStatus = orderStatus;
        this.deliveryAddress = deliveryAddress;
    }
}
