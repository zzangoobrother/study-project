package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;

public record ReadMessageAckRecord (
        UserId userId,
        ChannelId channelId,
        MessageSeqId messageSeqId
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.READ_MESSAGES_ACK;
    }
}
