package com.example.handler.kafka;

import com.example.dto.kafka.MessageNotificationRecord;
import com.example.service.MessageService;
import org.springframework.stereotype.Component;

@Component
public class MessageNotificationRecordHandler implements BaseRecordHandler<MessageNotificationRecord> {

    private final MessageService messageService;

    public MessageNotificationRecordHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void handleRecord(MessageNotificationRecord record) {
        messageService.sendMessage(record);
    }
}
