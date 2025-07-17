package com.example.handler.kafka;

import com.example.dto.kafka.WriteMessageRecord;
import com.example.service.MessageService;
import org.springframework.stereotype.Component;

@Component
public class WriteMessageHandler implements BaseRecordHandler<WriteMessageRecord> {

    private final MessageService messageService;

    public WriteMessageHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void handleRecord(WriteMessageRecord record) {
        messageService.sendMessage(record);
    }
}
