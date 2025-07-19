package com.example.handler.kafka;

import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseRecordHandler implements BaseRecordHandler<ErrorResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public ErrorResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(ErrorResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new ErrorResponse(record.messageType(), record.message()), record);
    }
}
