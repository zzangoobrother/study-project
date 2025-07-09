package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FetchMessageRequest extends BaseRequest {

    private final ChannelId channelId;
    private final MessageSeqId startMessageSeqId;
    private final MessageSeqId endMessageSeqId;

    @JsonCreator
    public FetchMessageRequest(@JsonProperty("channelId") ChannelId channelId, @JsonProperty("startMessageSeqId") MessageSeqId startMessageSeqId, @JsonProperty("startMessageSeqId") MessageSeqId endMessageSeqId) {
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
}
