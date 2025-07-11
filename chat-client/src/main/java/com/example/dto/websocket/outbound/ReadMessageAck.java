package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

public class ReadMessageAck extends BaseRequest {

    private final ChannelId channelId;
    private final MessageSeqId messageSeqId;

    @JsonCreator
    public ReadMessageAck(ChannelId channelId, MessageSeqId messageSeqId) {
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

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ReadMessageAck that = (ReadMessageAck) object;
        return Objects.equals(channelId, that.channelId) && Objects.equals(messageSeqId, that.messageSeqId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, messageSeqId);
    }
}
