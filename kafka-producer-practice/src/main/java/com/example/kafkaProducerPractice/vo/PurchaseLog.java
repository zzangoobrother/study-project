package com.example.kafkaProducerPractice.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class PurchaseLog {

    String orderId;
    String userId;
//    List<String> productId;
    List<Map<String, String>> productInfo;
    String purchasedDt;
//    Long price;
}
