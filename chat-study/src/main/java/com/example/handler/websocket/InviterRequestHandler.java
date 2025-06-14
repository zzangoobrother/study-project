package com.example.handler.websocket;

import com.example.constants.Constants;
import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.InviteRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.InviteNotification;
import com.example.dto.websocket.outbound.InviteResponse;
import com.example.service.UserConnectionService;
import com.example.session.WebSocketSessionManager;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class InviterRequestHandler implements BaseRequestHandler<InviteRequest> {

    private final UserConnectionService userConnectionService;
    private final WebSocketSessionManager webSocketSessionManager;

    public InviterRequestHandler(UserConnectionService userConnectionService,
                                 WebSocketSessionManager webSocketSessionManager) {
        this.userConnectionService = userConnectionService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, InviteRequest request) {
        UserId inviterUserId = (UserId)senderSession.getAttributes().get(Constants.USER_ID.getValue());
        Pair<Optional<UserId>, String> result = userConnectionService.invite(inviterUserId,
                request.getUserInviteCode());
        result.getFirst().ifPresentOrElse(partnerUserId -> {
            String inviterUsername = result.getSecond();
            webSocketSessionManager.sendMessage(senderSession, new InviteResponse(request.getUserInviteCode(), UserConnectionStatus.PENDING));
            webSocketSessionManager.sendMessage(webSocketSessionManager.getSession(partnerUserId), new InviteNotification(inviterUsername));
        }, () -> {
            String errorMessage = result.getSecond();
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.INVITE_REQUEST, errorMessage));
        });
    }
}
