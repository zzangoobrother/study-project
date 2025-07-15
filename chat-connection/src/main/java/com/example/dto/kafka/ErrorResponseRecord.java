package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;

public record ErrorResponseRecord (
        UserId userId,
        String messageType,
        String message
) implements RecordInterface {


    @Override
    public String type() {
        return MessageType.ERROR;
    }
}
