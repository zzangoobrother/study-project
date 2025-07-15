package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;

public record FetchMessageRequestRecord (
        UserId userId,
        ChannelId channelId,
        MessageSeqId startMessageSeqId,
        MessageSeqId endMessageSeqId
) implements RecordInterface {
    @Override
    public String type() {
        return MessageType.FETCH_MESSAGES_REQUEST;
    }
}
