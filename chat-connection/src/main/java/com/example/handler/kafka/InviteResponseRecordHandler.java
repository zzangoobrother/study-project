package com.example.handler.kafka;

import com.example.dto.kafka.InviteNotificationRecord;
import com.example.dto.kafka.InviteResponseRecord;
import com.example.dto.websocket.outbound.InviteNotification;
import com.example.dto.websocket.outbound.InviteResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class InviteResponseRecordHandler implements BaseRecordHandler<InviteResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public InviteResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(InviteResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new InviteResponse(record.inviteCode(), record.status()), record);
    }
}
