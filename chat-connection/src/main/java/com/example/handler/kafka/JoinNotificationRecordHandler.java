package com.example.handler.kafka;

import com.example.dto.kafka.InviteResponseRecord;
import com.example.dto.kafka.JoinNotificationRecord;
import com.example.dto.websocket.outbound.InviteResponse;
import com.example.dto.websocket.outbound.JoinNotification;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class JoinNotificationRecordHandler implements BaseRecordHandler<JoinNotificationRecord> {

    private final ClientNotificationService clientNotificationService;

    public JoinNotificationRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(JoinNotificationRecord record) {
        clientNotificationService.sendMessage(record.userId(), new JoinNotification(record.channelId(), record.title()), record);
    }
}
