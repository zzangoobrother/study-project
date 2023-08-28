package com.example.testcodewitharchitecture.mock;

import com.example.testcodewitharchitecture.common.service.port.ClockHolder;

public class TestClockHolder implements ClockHolder {

    private final long mills;

    public TestClockHolder(long mills) {
        this.mills = mills;
    }

    @Override
    public long millis() {
        return mills;
    }
}
