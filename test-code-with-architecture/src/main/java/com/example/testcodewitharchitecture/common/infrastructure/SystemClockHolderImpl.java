package com.example.testcodewitharchitecture.common.infrastructure;

import com.example.testcodewitharchitecture.common.service.port.ClockHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class SystemClockHolderImpl implements ClockHolder {
    @Override
    public long millis() {
        return Clock.systemUTC().millis();
    }
}
