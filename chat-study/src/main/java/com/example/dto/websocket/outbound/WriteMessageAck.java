package com.example.dto.websocket.outbound;

import com.example.constants.MessageType;
import com.example.dto.domain.MessageSeqId;

public class WriteMessageAck extends BaseMessage {

    private final Long serial;
    private final MessageSeqId messageSeqId;

    public WriteMessageAck(Long serial, MessageSeqId messageSeqId) {
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
