package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;

import java.util.List;

public class FetchMessageResponse extends BaseMessage {

    private final ChannelId channelId;
    private final List<Message> messages;

    public FetchMessageResponse(ChannelId channelId, List<Message> messages) {
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
