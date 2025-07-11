package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FetchMessageResponse extends BaseMessage {

    private final ChannelId channelId;
    private final List<Message> messages;

    @JsonCreator
    public FetchMessageResponse(@JsonProperty("channelId") ChannelId channelId, @JsonProperty("messages") List<Message> messages) {
        super(MessageType.FETCH_MESSAGES_RESPONSE);
        this.channelId = channelId;
        this.messages = messages;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public List<Message> getMessages() {
        return messages;
    }
}
