package com.example.handler.kafka;

import com.example.dto.kafka.LeaveResponseRecord;
import com.example.dto.websocket.outbound.LeaveResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class LeaveResponseRecordHandler implements BaseRecordHandler<LeaveResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public LeaveResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(LeaveResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new LeaveResponse(), record);
    }
}
