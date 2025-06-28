package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.QuitRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.QuitResponse;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class QuitRequestHandler implements BaseRequestHandler<QuitRequest> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public QuitRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, QuitRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        ResultType result;
        try {
            result = channelService.quit(request.getChannelId(), senderUserId);
        } catch (Exception ex) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.QUIT_REQUEST, ResultType.FAILED.getMessage()));
            return;
        }

        if (result == ResultType.SUCCESS) {
            clientNotificationService.sendMessage(senderSession, senderUserId, new QuitResponse(request.getChannelId()));
        } else {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.QUIT_REQUEST, result.getMessage()));
        }
    }
}
