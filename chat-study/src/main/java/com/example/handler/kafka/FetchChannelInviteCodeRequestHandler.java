package com.example.handler.kafka;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.ErrorResponseRecord;
import com.example.dto.kafka.FetchChannelInviteCodeRequestRecord;
import com.example.dto.kafka.FetchChannelInviteCodeResponseRecord;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;

@Component
public class FetchChannelInviteCodeRequestHandler implements BaseRecordHandler<FetchChannelInviteCodeRequestRecord> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public FetchChannelInviteCodeRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRecord(FetchChannelInviteCodeRequestRecord record) {
        UserId senderUserId = record.userId();

        if (!channelService.isJoined(record.channelId(), senderUserId)) {
            clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Not joined the channel."));
            return;
        }

        channelService.getInviteCode(record.channelId())
                .ifPresentOrElse(inviteCode ->
                                clientNotificationService.sendMessage(senderUserId, new FetchChannelInviteCodeResponseRecord(senderUserId, record.channelId(), inviteCode)),
                        () -> clientNotificationService.sendError(new ErrorResponseRecord(senderUserId, MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Fetch channel invite code failed."))
                );
    }
}
