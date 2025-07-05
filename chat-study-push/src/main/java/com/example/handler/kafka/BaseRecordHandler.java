package com.example.handler.kafka;

import com.example.dto.kafka.outbound.RecordInterface;

public interface BaseRecordHandler<T extends RecordInterface> {
    void handleRecord(T record);
}
