package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.InviteRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.InviteNotification;
import com.example.dto.websocket.outbound.InviteResponse;
import com.example.service.ClientNotificationService;
import com.example.service.UserConnectionService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class InviterRequestHandler implements BaseRequestHandler<InviteRequest> {

    private final UserConnectionService userConnectionService;
    private final ClientNotificationService clientNotificationService;

    public InviterRequestHandler(UserConnectionService userConnectionService, ClientNotificationService clientNotificationService) {
        this.userConnectionService = userConnectionService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, InviteRequest request) {
        UserId inviterUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        Pair<Optional<UserId>, String> result = userConnectionService.invite(inviterUserId,
                request.getUserInviteCode());
        result.getFirst().ifPresentOrElse(partnerUserId -> {
            String inviterUsername = result.getSecond();
            clientNotificationService.sendMessage(senderSession, inviterUserId, new InviteResponse(request.getUserInviteCode(), UserConnectionStatus.PENDING));
            clientNotificationService.sendMessage(partnerUserId, new InviteNotification(inviterUsername));
        }, () -> {
            String errorMessage = result.getSecond();
            clientNotificationService.sendMessage(senderSession, inviterUserId, new ErrorResponse(MessageType.INVITE_REQUEST, errorMessage));
        });
    }
}
