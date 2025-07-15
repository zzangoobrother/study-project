package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;

import java.util.List;

public record CreateRequestRecord (
        UserId userId,
        String title,
        List<String> participantUsernames
) implements RecordInterface {
    @Override
    public String type() {
        return MessageType.CREATE_REQUEST;
    }
}
