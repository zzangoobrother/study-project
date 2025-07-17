package com.example.handler.kafka;

import com.example.dto.kafka.ReadMessageAckRecord;
import com.example.service.MessageService;
import org.springframework.stereotype.Component;

@Component
public class ReadMessageAckHandler implements BaseRecordHandler<ReadMessageAckRecord> {

    private final MessageService messageService;

    public ReadMessageAckHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void handleRecord(ReadMessageAckRecord record) {
        messageService.updateLastReadMsgSeq(record.userId(), record.channelId(), record.messageSeqId());
    }
}
