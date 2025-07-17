package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.Channel;
import com.example.dto.domain.UserId;

import java.util.List;

public record FetchChannelsResponseRecord (
        UserId userId,
        List<Channel> channels
) implements RecordInterface {

    @Override
    public String type() {
        return MessageType.FETCH_CHANNELS_RESPONSE;
    }
}
