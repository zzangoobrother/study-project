package com.example.testcodewitharchitecture.common.infrastructure;

import com.example.testcodewitharchitecture.common.service.port.UuidHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SystemUuidHolder implements UuidHolder {
    @Override
    public String random() {
        return UUID.randomUUID().toString();
    }
}
