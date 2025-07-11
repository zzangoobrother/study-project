package com.example.dto.websocket.inbound;

import com.example.constants.MessageType;
import com.example.dto.domain.MessageSeqId;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class WriteMessageAck extends BaseMessage {

    private final Long serial;
    private final MessageSeqId messageSeqId;

    @JsonCreator
    public WriteMessageAck(@JsonProperty("serial") Long serial, @JsonProperty("messageSeqId") MessageSeqId messageSeqId) {
        super(MessageType.WRITE_MESSAGES_ACK);
        this.serial = serial;
        this.messageSeqId = messageSeqId;
    }

    public Long getSerial() {
        return serial;
    }

    public MessageSeqId getMessageSeqId() {
        return messageSeqId;
    }
}
