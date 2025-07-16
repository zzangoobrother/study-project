package com.example.handler.kafka;

import com.example.dto.kafka.FetchChannelsResponseRecord;
import com.example.dto.websocket.outbound.FetchChannelsResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchChannelsResponseRecordHandler implements BaseRecordHandler<FetchChannelsResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public FetchChannelsResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchChannelsResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new FetchChannelsResponse(record.channels()), record);
    }
}
