package com.example.handler.kafka;

import com.example.dto.kafka.AcceptResponseRecord;
import com.example.dto.websocket.outbound.AcceptResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class AcceptResponseRecordHandler implements BaseRecordHandler<AcceptResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public AcceptResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(AcceptResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new AcceptResponse(record.username()), record);
    }
}
