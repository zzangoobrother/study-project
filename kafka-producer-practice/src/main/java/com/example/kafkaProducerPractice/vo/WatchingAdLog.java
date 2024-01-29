package com.example.kafkaProducerPractice.vo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WatchingAdLog {

    String userId;
    String productId;
    String adId;
    String adType;
    String watchingTime;
    String watchingDt;
}
