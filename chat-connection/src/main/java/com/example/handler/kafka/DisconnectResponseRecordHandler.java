package com.example.handler.kafka;

import com.example.dto.kafka.CreateResponseRecord;
import com.example.dto.kafka.DisconnectResponseRecord;
import com.example.dto.websocket.outbound.CreateResponse;
import com.example.dto.websocket.outbound.DisconnectResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class DisconnectResponseRecordHandler implements BaseRecordHandler<DisconnectResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public DisconnectResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(DisconnectResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new DisconnectResponse(record.username(), record.status()), record);
    }
}
