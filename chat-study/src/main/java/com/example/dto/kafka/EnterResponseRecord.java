package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;

public record EnterResponseRecord (
        UserId userId,
        ChannelId channelId,
        String title,
        MessageSeqId lastReadMessageSeqId,
        MessageSeqId lastChannelMessageSeqId
) implements RecordInterface {
    @Override
    public String type() {
        return MessageType.ENTER_RESPONSE;
    }
}
