package com.example.fastcampuseventdrivenpayment;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OrderEventStreamListener implements StreamListener<String, MapRecord<String, String, String>> {
    int paymentProductId = 0;

    private final RedisTemplate redisTemplate;

    public OrderEventStreamListener(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        Map<String, String> map = message.getValue();

        String userId = map.get("userId");
        String productId = map.get("productId");
        String price = map.get("price");

        // 결제 관련 로직 처리

        String paymentIdStr = Integer.toString(paymentProductId++);

        // 결제 완료 이벤트 발행
        Map<String, String> fieldMap = new HashMap<>();
        fieldMap.put("userId", userId);
        fieldMap.put("productId", productId);
        fieldMap.put("price", price);
        fieldMap.put("paymentProcessId", paymentIdStr);

        redisTemplate.opsForStream().add("payment-event", fieldMap);

        System.out.println("[Order consumed] Created payment : " + paymentIdStr);
    }
}
