package com.example.entity;

import com.example.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "delivery", indexes = {@Index(name = "idx_orderId", columnList = "orderId")})
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private String productName;

    private Long productCount;

    private String address;

    private DeliveryStatus deliveryStatus;

    private Long referenceCode;

    public Delivery(Long orderId, String productName, Long productCount, String address, DeliveryStatus deliveryStatus, Long referenceCode) {
        this.orderId = orderId;
        this.productName = productName;
        this.productCount = productCount;
        this.address = address;
        this.deliveryStatus = deliveryStatus;
        this.referenceCode = referenceCode;
    }

    public void updateStatus(DeliveryStatus status) {
        this.deliveryStatus = status;
    }
}
