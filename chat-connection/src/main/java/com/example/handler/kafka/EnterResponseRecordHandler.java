package com.example.handler.kafka;

import com.example.dto.kafka.DisconnectResponseRecord;
import com.example.dto.kafka.EnterResponseRecord;
import com.example.dto.websocket.outbound.DisconnectResponse;
import com.example.dto.websocket.outbound.EnterResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class EnterResponseRecordHandler implements BaseRecordHandler<EnterResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public EnterResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(EnterResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new EnterResponse(record.channelId(), record.title(), record.lastReadMessageSeqId(), record.lastChannelMessageSeqId()), record);
    }
}
