package com.example.handler.kafka;

import com.example.dto.kafka.FetchConnectionsResponseRecord;
import com.example.dto.websocket.outbound.FetchConnectionsResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchConnectionsResponseRecordHandler implements BaseRecordHandler<FetchConnectionsResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public FetchConnectionsResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchConnectionsResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new FetchConnectionsResponse(record.connections()), record);
    }
}
