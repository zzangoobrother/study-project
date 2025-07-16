package com.example.handler.kafka;

import com.example.dto.kafka.FetchConnectionsResponseRecord;
import com.example.dto.kafka.FetchMessageResponseRecord;
import com.example.dto.websocket.outbound.FetchConnectionsResponse;
import com.example.dto.websocket.outbound.FetchMessageResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchMessageResponseRecordHandler implements BaseRecordHandler<FetchMessageResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public FetchMessageResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchMessageResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new FetchMessageResponse(record.channelId(), record.messages()), record);
    }
}
