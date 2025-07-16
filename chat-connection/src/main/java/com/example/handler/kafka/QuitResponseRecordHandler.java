package com.example.handler.kafka;

import com.example.dto.kafka.QuitResponseRecord;
import com.example.dto.websocket.outbound.QuitResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class QuitResponseRecordHandler implements BaseRecordHandler<QuitResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public QuitResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(QuitResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new QuitResponse(record.channelId()), record);
    }
}
