package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchUserInvitecodeRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.FetchUserInvitecodeResponse;
import com.example.service.ClientNotificationService;
import com.example.service.UserService;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchUserInvitecodeRequestHandler implements BaseRequestHandler<FetchUserInvitecodeRequest> {

    private final UserService userService;
    private final ClientNotificationService clientNotificationService;

    public FetchUserInvitecodeRequestHandler(UserService userService, ClientNotificationService clientNotificationService) {
        this.userService = userService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchUserInvitecodeRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        userService.getInviteCode(senderUserId)
                .ifPresentOrElse(
                        inviteCode -> clientNotificationService.sendMessage(senderSession, senderUserId, new FetchUserInvitecodeResponse(inviteCode)),
                        () -> clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.FETCH_USER_INVITECODE_REQUEST, "Fetch user invite code failed."))
                );
    }
}
