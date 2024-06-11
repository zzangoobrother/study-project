package com.example.outboxPatternConsumerPractice.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders_detail")
@Entity
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity")
    private int quantity;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    public OrderDetail(Long orderId, Long productId, int quantity, LocalDateTime createAt) {
        this.productId = productId;
        this.quantity = quantity;
        this.createAt = createAt;
    }
}
