package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.RejectRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.RejectResponse;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RejectRequestHandler implements BaseRequestHandler<RejectRequest> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public RejectRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, RejectRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        Pair<Boolean, String> result = userConnectionService.reject(senderUserId, request.getUsername());
        if (result.getFirst()) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new RejectResponse(request.getUsername(), UserConnectionStatus.REJECTED));
        } else {
            String errorMessage = result.getSecond();
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.REJECT_REQUEST, errorMessage));
        }
    }
}
