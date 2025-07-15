package com.example.dto.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.outbound.BaseMessage;

public record WriteMessageAckRecord (
        UserId userId,
        Long serial,
        MessageSeqId messageSeqId
) implements RecordInterface {


    @Override
    public String type() {
        return MessageType.WRITE_MESSAGES_ACK;
    }
}
