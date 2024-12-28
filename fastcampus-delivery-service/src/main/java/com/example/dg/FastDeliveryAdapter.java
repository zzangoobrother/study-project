package com.example.dg;

import org.springframework.stereotype.Service;

@Service
public class FastDeliveryAdapter implements DeliveryAdapter {
    @Override
    public Long processDelivery(String productName, Long productCount, String address) {
        return 11111L;
    }
}
