package com.example.kafkaProducerPractice.vo;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class PurchaseLog {

    String orderId;
    String userId;
//    List<String> productId;
    List<Map<String, String>> productInfo;
    String purchasedDt;
//    Long price;
}
