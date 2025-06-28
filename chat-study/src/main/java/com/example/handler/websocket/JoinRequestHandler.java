package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.Channel;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.JoinRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.JoinResponse;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class JoinRequestHandler implements BaseRequestHandler<JoinRequest> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public JoinRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, JoinRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        Pair<Optional<Channel>, ResultType> result;
        try {
            result = channelService.join(request.getInviteCode(), senderUserId);
        } catch (Exception ex) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.JOIN_REQUEST, ResultType.FAILED.getMessage()));
            return;
        }

        result.getFirst().ifPresentOrElse(
                channel -> clientNotificationService.sendMessage(senderSession, senderUserId, new JoinResponse(channel.channelId(), channel.title())),
                () -> clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.JOIN_REQUEST, result.getSecond().getMessage()))
        );
    }
}
