package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchMessageRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.FetchMessageResponse;
import com.example.service.ClientNotificationService;
import com.example.service.MessageService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

@Component
public class FetchMessagesRequestHandler implements BaseRequestHandler<FetchMessageRequest> {

    private final MessageService messageService;
    private final ClientNotificationService clientNotificationService;

    public FetchMessagesRequestHandler(MessageService messageService, ClientNotificationService clientNotificationService) {
        this.messageService = messageService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchMessageRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        ChannelId channelId = request.getChannelId();
        Pair<List<Message>, ResultType> result = messageService.getMessages(channelId, request.getStartMessageSeqId(), request.getEndMessageSeqId());
        if (result.getSecond() == ResultType.SUCCESS) {
            List<Message> messages = result.getFirst();
            clientNotificationService.sendMessage(senderSession, senderUserId, new FetchMessageResponse(channelId, messages));
        } else {
            clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.FETCH_MESSAGES_REQUEST, result.getSecond().getMessage()));
        }
    }
}
