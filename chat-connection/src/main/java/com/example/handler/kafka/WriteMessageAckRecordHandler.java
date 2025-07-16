package com.example.handler.kafka;

import com.example.dto.kafka.WriteMessageAckRecord;
import com.example.dto.websocket.outbound.WriteMessageAck;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class WriteMessageAckRecordHandler implements BaseRecordHandler<WriteMessageAckRecord> {

    private final ClientNotificationService clientNotificationService;

    public WriteMessageAckRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(WriteMessageAckRecord record) {
        clientNotificationService.sendMessage(record.userId(), new WriteMessageAck(record.serial(), record.messageSeqId()), record);
    }
}
