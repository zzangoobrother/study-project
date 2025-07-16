package com.example.handler.kafka;

import com.example.dto.kafka.InviteNotificationRecord;
import com.example.dto.websocket.outbound.InviteNotification;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class InviteNotificationRecordHandler implements BaseRecordHandler<InviteNotificationRecord> {

    private final ClientNotificationService clientNotificationService;

    public InviteNotificationRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(InviteNotificationRecord record) {
        clientNotificationService.sendMessage(record.userId(), new InviteNotification(record.username()), record);
    }
}
