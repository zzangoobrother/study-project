package com.example.handler.kafka;

import com.example.dto.kafka.CreateResponseRecord;
import com.example.dto.websocket.outbound.CreateResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class CreateResponseRecordHandler implements BaseRecordHandler<CreateResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public CreateResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(CreateResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new CreateResponse(record.channelId(), record.title()), record);
    }
}
