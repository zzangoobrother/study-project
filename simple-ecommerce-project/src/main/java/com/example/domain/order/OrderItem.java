package com.example.domain.order;

import com.example.domain.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

@Setter
@Getter
@Entity
@Table(name = "order_items", schema = "ecommerce")
public class OrderItem extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "quantity")
    private int quantity = 0;

    @Column(name = "price")
    private BigDecimal price = BigDecimal.ZERO;
}
