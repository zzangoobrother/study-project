package com.example.handler.kafka;

import com.example.dto.kafka.QuitResponseRecord;
import com.example.dto.kafka.RejectResponseRecord;
import com.example.dto.websocket.outbound.QuitResponse;
import com.example.dto.websocket.outbound.RejectResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class RejectResponseRecordHandler implements BaseRecordHandler<RejectResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public RejectResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(RejectResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new RejectResponse(record.username(), record.status()), record);
    }
}
