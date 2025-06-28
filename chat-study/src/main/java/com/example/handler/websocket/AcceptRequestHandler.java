package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.AcceptRequest;
import com.example.dto.websocket.outbound.AcceptNotification;
import com.example.dto.websocket.outbound.AcceptResponse;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class AcceptRequestHandler implements BaseRequestHandler<AcceptRequest> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public AcceptRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, AcceptRequest request) {
        UserId acceptorUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        Pair<Optional<UserId>, String> result = userConnectionService.accept(acceptorUserId, request.getUsername());
        result.getFirst().ifPresentOrElse(inviterUserId -> {
            clientNotificationService.sendMessage(senderSession, acceptorUserId, new AcceptResponse(request.getUsername()));
            String acceptorUsername = result.getSecond();
            clientNotificationService.sendMessage(inviterUserId, new AcceptNotification(acceptorUsername));
        }, () -> {
            String errorMessage = result.getSecond();
            clientNotificationService.sendMessage(senderSession, acceptorUserId, new ErrorResponse(MessageType.ACCEPT_REQUEST, errorMessage));
        });
    }
}
