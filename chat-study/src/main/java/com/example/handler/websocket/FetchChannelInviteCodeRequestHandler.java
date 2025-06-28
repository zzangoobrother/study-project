package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchChannelInviteCodeRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.FetchChannelInviteCodeResponse;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchChannelInviteCodeRequestHandler implements BaseRequestHandler<FetchChannelInviteCodeRequest
        > {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public FetchChannelInviteCodeRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchChannelInviteCodeRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        if (!channelService.isJoined(request.getChannelId(), senderUserId)) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Not joined the channel."));
            return;
        }

        channelService.getInviteCode(request.getChannelId())
                .ifPresentOrElse(inviteCode ->
                                clientNotificationService.sendMessage(senderSession, senderUserId, new FetchChannelInviteCodeResponse(request.getChannelId(), inviteCode)),
                        () -> clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Fetch channel invite code failed."))
                );
    }
}
