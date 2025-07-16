package com.example.handler.kafka;

import com.example.dto.kafka.FetchUserInvitecodeResponseRecord;
import com.example.dto.websocket.outbound.FetchUserInvitecodeResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchUserInvitecodeResponseRecordHandler implements BaseRecordHandler<FetchUserInvitecodeResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public FetchUserInvitecodeResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchUserInvitecodeResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new FetchUserInvitecodeResponse(record.inviteCode()), record);
    }
}
