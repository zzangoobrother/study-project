package com.example.handler.kafka;

import com.example.dto.kafka.AcceptNotificationRecord;
import com.example.dto.websocket.outbound.AcceptNotification;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class AcceptNotificationRecordHandler implements BaseRecordHandler<AcceptNotificationRecord> {

    private final ClientNotificationService clientNotificationService;

    public AcceptNotificationRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(AcceptNotificationRecord record) {
        clientNotificationService.sendMessage(record.userId(), new AcceptNotification(record.username()), record);
    }
}
