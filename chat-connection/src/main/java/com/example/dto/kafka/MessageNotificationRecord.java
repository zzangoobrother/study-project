package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;

import java.util.List;

public record MessageNotificationRecord (
        UserId userId,
        ChannelId channelId,
        String username,
        String content,
        List<UserId> participantIds
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.NOTIFY_MESSAGE;
    }
}
