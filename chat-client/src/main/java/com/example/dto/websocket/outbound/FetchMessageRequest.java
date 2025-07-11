package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

public class FetchMessageRequest extends BaseRequest {

    private final ChannelId channelId;
    private final MessageSeqId startMessageSeqId;
    private final MessageSeqId endMessageSeqId;

    @JsonCreator
    public FetchMessageRequest(ChannelId channelId, MessageSeqId startMessageSeqId, MessageSeqId endMessageSeqId) {
        super(MessageType.FETCH_MESSAGES_REQUEST);
        this.channelId = channelId;
        this.startMessageSeqId = startMessageSeqId;
        this.endMessageSeqId = endMessageSeqId;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public MessageSeqId getStartMessageSeqId() {
        return startMessageSeqId;
    }

    public MessageSeqId getEndMessageSeqId() {
        return endMessageSeqId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        FetchMessageRequest that = (FetchMessageRequest) object;
        return Objects.equals(channelId, that.channelId) && Objects.equals(startMessageSeqId, that.startMessageSeqId) && Objects.equals(endMessageSeqId, that.endMessageSeqId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, startMessageSeqId, endMessageSeqId);
    }
}
