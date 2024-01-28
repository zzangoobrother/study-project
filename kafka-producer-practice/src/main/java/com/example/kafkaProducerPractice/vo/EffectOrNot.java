package com.example.kafkaProducerPractice.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class EffectOrNot {
    String adId;
    String userId;
    String orderId;
    Map<String, String> productInfo;
}
