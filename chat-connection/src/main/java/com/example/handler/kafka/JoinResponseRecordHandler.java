package com.example.handler.kafka;

import com.example.dto.kafka.JoinResponseRecord;
import com.example.dto.websocket.outbound.JoinNotification;
import com.example.dto.websocket.outbound.JoinResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class JoinResponseRecordHandler implements BaseRecordHandler<JoinResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public JoinResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(JoinResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new JoinResponse(record.channelId(), record.title()), record);
    }
}
