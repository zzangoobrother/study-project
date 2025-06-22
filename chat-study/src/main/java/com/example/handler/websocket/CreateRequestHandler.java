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
import com.example.service.UserService;
import com.example.session.WebSocketSessionManager;
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
    private final WebSocketSessionManager webSocketSessionManager;

    public CreateRequestHandler(ChannelService channelService, UserService userService, WebSocketSessionManager webSocketSessionManager) {
        this.channelService = channelService;
        this.userService = userService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, CreateRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        List<UserId> participantIds = userService.getUserIds(request.getParticipantUsernames());
        if (participantIds.isEmpty()) {
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.CREATE_REQUEST, ResultType.NOT_FOUND.getMessage()));
            return;
        }

        Pair<Optional<Channel>, ResultType> result;
        try {
            result = channelService.create(senderUserId, participantIds, request.getTitle());
        } catch (Exception ex) {
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.CREATE_REQUEST, ResultType.FAILED.getMessage()));
            return;
        }

        if (result.getFirst().isEmpty()) {
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.CREATE_REQUEST, result.getSecond().getMessage()));
            return;
        }

        Channel channel = result.getFirst().get();
        webSocketSessionManager.sendMessage(senderSession, new CreateResponse(channel.channelId(), channel.title()));
        participantIds.forEach(participantId -> {
            CompletableFuture.runAsync(() -> {
                WebSocketSession participantSession = webSocketSessionManager.getSession(participantId);
                if (participantSession != null) {
                    webSocketSessionManager.sendMessage(
                            participantSession,
                            new JoinNotification(channel.channelId(), channel.title()));
                }
            });
        });
    }
}
