package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.EnterRequest;
import com.example.dto.websocket.outbound.EnterResponse;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.service.ChannelService;
import com.example.session.WebSocketSessionManager;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class EnterRequestHandler implements BaseRequestHandler<EnterRequest> {

    private final ChannelService channelService;
    private final WebSocketSessionManager webSocketSessionManager;

    public EnterRequestHandler(ChannelService channelService, WebSocketSessionManager webSocketSessionManager) {
        this.channelService = channelService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, EnterRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        Pair<Optional<String>, ResultType> result = channelService.enter(request.getChannelId(), senderUserId);
        result.getFirst()
                .ifPresentOrElse(
                        title -> webSocketSessionManager.sendMessage(senderSession, new EnterResponse(request.getChannelId(), title)),
                                () -> webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.ENTER_REQUEST, result.getSecond().getMessage()))
                );
    }
}
