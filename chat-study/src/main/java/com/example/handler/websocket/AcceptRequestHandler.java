package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.AcceptRequest;
import com.example.dto.websocket.outbound.AcceptNotification;
import com.example.dto.websocket.outbound.AcceptResponse;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.service.UserConnectionService;
import com.example.session.WebSocketSessionManager;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class AcceptRequestHandler implements BaseRequestHandler<AcceptRequest> {

    private final UserConnectionService userConnectionService;
    private final WebSocketSessionManager webSocketSessionManager;

    public AcceptRequestHandler(UserConnectionService userConnectionService,
                                WebSocketSessionManager webSocketSessionManager) {
        this.userConnectionService = userConnectionService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, AcceptRequest request) {
        UserId acceptorUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        Pair<Optional<UserId>, String> result = userConnectionService.accept(acceptorUserId, request.getUsername());
        result.getFirst().ifPresentOrElse(inviterUserId -> {
            webSocketSessionManager.sendMessage(senderSession, new AcceptResponse(request.getUsername()));
            String acceptorUsername = result.getSecond();
            webSocketSessionManager.sendMessage(webSocketSessionManager.getSession(inviterUserId), new AcceptNotification(acceptorUsername));
        }, () -> {
            String errorMessage = result.getSecond();
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.ACCEPT_REQUEST, errorMessage));
        });
    }
}
