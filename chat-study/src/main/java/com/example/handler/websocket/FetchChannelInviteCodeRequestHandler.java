package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchChannelInviteCodeRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.FetchChannelInviteCodeResponse;
import com.example.service.ChannelService;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchChannelInviteCodeRequestHandler implements BaseRequestHandler<FetchChannelInviteCodeRequest
        > {

    private final ChannelService channelService;
    private final WebSocketSessionManager webSocketSessionManager;

    public FetchChannelInviteCodeRequestHandler(ChannelService channelService, WebSocketSessionManager webSocketSessionManager) {
        this.channelService = channelService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchChannelInviteCodeRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        if (!channelService.isJoined(request.getChannelId(), senderUserId)) {
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Not joined the channel."));
            return;
        }

        channelService.getInviteCode(request.getChannelId())
                .ifPresentOrElse(inviteCode ->
                        webSocketSessionManager.sendMessage(senderSession, new FetchChannelInviteCodeResponse(request.getChannelId(), inviteCode)),
                        () -> webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.FETCH_CHANNEL_INVITECODE_REQUEST, "Fetch channel invite code failed."))
                );
    }
}
