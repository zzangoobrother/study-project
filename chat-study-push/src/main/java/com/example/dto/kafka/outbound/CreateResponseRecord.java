package com.example.dto.kafka.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;

public record CreateResponseRecord (
        UserId userId,
        ChannelId channelId,
        String title
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.CREATE_RESPONSE;
    }
}
