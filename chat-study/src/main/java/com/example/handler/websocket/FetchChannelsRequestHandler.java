package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.FetchChannelsRequest;
import com.example.dto.websocket.outbound.FetchChannelsResponse;
import com.example.service.ChannelService;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class FetchChannelsRequestHandler implements BaseRequestHandler<FetchChannelsRequest> {

    private final ChannelService channelService;
    private final WebSocketSessionManager webSocketSessionManager;

    public FetchChannelsRequestHandler(ChannelService channelService,
                                       WebSocketSessionManager webSocketSessionManager) {
        this.channelService = channelService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, FetchChannelsRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());
        webSocketSessionManager.sendMessage(senderSession, new FetchChannelsResponse(channelService.getChannels(senderUserId)));
    }
}
