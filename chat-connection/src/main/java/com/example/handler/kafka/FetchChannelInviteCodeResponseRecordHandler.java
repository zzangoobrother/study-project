package com.example.handler.kafka;

import com.example.dto.kafka.FetchChannelInviteCodeResponseRecord;
import com.example.dto.websocket.outbound.FetchChannelInviteCodeResponse;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchChannelInviteCodeResponseRecordHandler implements BaseRecordHandler<FetchChannelInviteCodeResponseRecord> {

    private final ClientNotificationService clientNotificationService;

    public FetchChannelInviteCodeResponseRecordHandler(ClientNotificationService clientNotificationService) {
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchChannelInviteCodeResponseRecord record) {
        clientNotificationService.sendMessage(record.userId(), new FetchChannelInviteCodeResponse(record.channelId(), record.inviteCode()), record);
    }
}
