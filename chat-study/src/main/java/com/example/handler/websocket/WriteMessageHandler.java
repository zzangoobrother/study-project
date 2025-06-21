package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.WriteMessage;
import com.example.dto.websocket.outbound.MessageNotification;
import com.example.service.MessageService;
import com.example.service.UserService;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WriteMessageHandler implements BaseRequestHandler<WriteMessage> {

    private final UserService userService;
    private final MessageService messageService;
    private final WebSocketSessionManager webSocketSessionManager;

    public WriteMessageHandler(UserService userService, MessageService messageService, WebSocketSessionManager webSocketSessionManager) {
        this.userService = userService;
        this.messageService = messageService;
        this.webSocketSessionManager = webSocketSessionManager;
    }


    @Override
    public void handleRequest(WebSocketSession senderSession, WriteMessage request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        ChannelId channelId = request.getChannelId();
        String content = request.getContent();
        String senderUsername = userService.getUsername(senderUserId).orElse("unKnown");
        messageService.sendMessage(senderUserId, content, channelId, (participantId) -> {
            WebSocketSession participantSession = webSocketSessionManager.getSession(participantId);
            MessageNotification messageNotification = new MessageNotification(channelId, senderUsername, content);
            if (participantSession != null) {
                webSocketSessionManager.sendMessage(participantSession, messageNotification);
            }
        });
    }
}
