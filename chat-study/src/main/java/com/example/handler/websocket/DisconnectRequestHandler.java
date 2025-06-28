package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.DisconnectRequest;
import com.example.dto.websocket.outbound.DisconnectResponse;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class DisconnectRequestHandler implements BaseRequestHandler<DisconnectRequest> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public DisconnectRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, DisconnectRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        Pair<Boolean, String> result = userConnectionService.disconnect(senderUserId, request.getUsername());
        if (result.getFirst()) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new DisconnectResponse(request.getUsername(), UserConnectionStatus.DISCONNECTED));
        } else {
            String errorMessage = result.getSecond();
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.DISCONNECT_REQUEST, errorMessage));
        }
    }
}
