package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ReadMessageAck extends BaseRequest {

    private final ChannelId channelId;
    private final MessageSeqId messageSeqId;

    @JsonCreator
    public ReadMessageAck(@JsonProperty("channelId") ChannelId channelId, @JsonProperty("content") MessageSeqId messageSeqId) {
        super(MessageType.READ_MESSAGES_ACK);
        this.channelId = channelId;
        this.messageSeqId = messageSeqId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public MessageSeqId getMessageSeqId() {
        return messageSeqId;
    }
}
