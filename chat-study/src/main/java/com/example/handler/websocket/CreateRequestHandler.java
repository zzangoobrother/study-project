package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.Channel;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.CreateRequest;
import com.example.dto.websocket.outbound.CreateResponse;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.JoinNotification;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import com.example.service.UserService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateRequestHandler implements BaseRequestHandler<CreateRequest> {

    private final ChannelService channelService;
    private final UserService userService;
    private final ClientNotificationService clientNotificationService;

    public CreateRequestHandler(ChannelService channelService, UserService userService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.userService = userService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, CreateRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        List<UserId> participantIds = userService.getUserIds(request.getParticipantUsernames());
        if (participantIds.isEmpty()) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.CREATE_REQUEST, ResultType.NOT_FOUND.getMessage()));
            return;
        }

        Pair<Optional<Channel>, ResultType> result;
        try {
            result = channelService.create(senderUserId, participantIds, request.getTitle());
        } catch (Exception ex) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.CREATE_REQUEST, ResultType.FAILED.getMessage()));
            return;
        }

        if (result.getFirst().isEmpty()) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.CREATE_REQUEST, result.getSecond().getMessage()));
            return;
        }

        Channel channel = result.getFirst().get();
        clientNotificationService.sendMessage(senderSession, senderUserId, new CreateResponse(channel.channelId(), channel.title()));
        participantIds.forEach(participantId -> CompletableFuture.runAsync(() -> clientNotificationService.sendMessage(participantId, new JoinNotification(channel.channelId(), channel.title()))));
    }
}
