package com.example.domain.order;

import com.example.domain.BaseEntity;
import com.example.enums.PayType;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.math.BigDecimal;

@Setter
@Getter
@Entity
@Table(name = "orders", schema = "ecommerce")
public class Order extends BaseEntity {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "pay_type")
    @Enumerated(value = EnumType.STRING)
    private PayType payType;
}
