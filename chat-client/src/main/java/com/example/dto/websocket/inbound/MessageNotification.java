package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MessageNotification extends BaseMessage {

    private final ChannelId channelId;
    private final String username;
    private final String content;

    @JsonCreator
    public MessageNotification(@JsonProperty("channelId") ChannelId channelId, @JsonProperty("username") String username, @JsonProperty("content") String content) {
        super(MessageType.NOTIFY_MESSAGE);
        this.channelId = channelId;
        this.username = username;
        this.content = content;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }
}
