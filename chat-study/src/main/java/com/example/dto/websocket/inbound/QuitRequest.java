package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class QuitRequest extends BaseRequest {

    private final ChannelId channelId;

    @JsonCreator
    public QuitRequest(@JsonProperty("channelId") ChannelId channelId) {
        super(MessageType.QUIT_REQUEST);
        this.channelId = channelId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }
}
