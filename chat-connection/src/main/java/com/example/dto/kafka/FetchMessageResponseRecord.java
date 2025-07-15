package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;
import com.example.dto.domain.UserId;

import java.util.List;

public record FetchMessageResponseRecord (
        UserId userId,
        ChannelId channelId,
        List<Message> messages
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.FETCH_MESSAGES_RESPONSE;
    }
}
