package com.example.kafkaProducerPractice.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseLogOneProduct {
    String orderId;
    String userId;
    String productId;
    String purchasedDt;
    String price;
}
