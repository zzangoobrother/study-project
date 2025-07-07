package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.ReadMessageAck;
import com.example.service.MessageService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ReadMessageAckHandler implements BaseRequestHandler<ReadMessageAck> {

    private final MessageService messageService;

    public ReadMessageAckHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, ReadMessageAck request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        messageService.updateLastReadMsgSeq(senderUserId, request.getChannelId(), request.getMessageSeqId());
    }
}
