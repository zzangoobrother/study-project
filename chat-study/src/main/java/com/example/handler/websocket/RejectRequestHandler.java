package com.example.handler.websocket;

import com.example.constants.Constants;
import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.RejectRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.RejectResponse;
import com.example.service.UserConnectionService;
import com.example.session.WebSocketSessionManager;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class RejectRequestHandler implements BaseRequestHandler<RejectRequest> {

    private final UserConnectionService userConnectionService;
    private final WebSocketSessionManager webSocketSessionManager;

    public RejectRequestHandler(UserConnectionService userConnectionService,
                                WebSocketSessionManager webSocketSessionManager) {
        this.userConnectionService = userConnectionService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, RejectRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(Constants.USER_ID.getValue());
        Pair<Boolean, String> result = userConnectionService.reject(senderUserId, request.getUsername());
        if (result.getFirst()) {
            webSocketSessionManager.sendMessage(senderSession, new RejectResponse(request.getUsername(), UserConnectionStatus.REJECTED));
        } else {
            String errorMessage = result.getSecond();
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.REJECT_REQUEST, errorMessage));
        }
    }
}
