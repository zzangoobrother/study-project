package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.ChannelEntry;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.EnterRequest;
import com.example.dto.websocket.outbound.EnterResponse;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.service.ChannelService;
import com.example.service.ClientNotificationService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Component
public class EnterRequestHandler implements BaseRequestHandler<EnterRequest> {

    private final ChannelService channelService;
    private final ClientNotificationService clientNotificationService;

    public EnterRequestHandler(ChannelService channelService, ClientNotificationService clientNotificationService) {
        this.channelService = channelService;
        this.clientNotificationService = clientNotificationService;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, EnterRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        Pair<Optional<ChannelEntry>, ResultType> result = channelService.enter(request.getChannelId(), senderUserId);
        result.getFirst()
                .ifPresentOrElse(
                        channelEntry -> clientNotificationService.sendMessage(senderSession, senderUserId, new EnterResponse(request.getChannelId(), channelEntry.title(), channelEntry.lastReadMessageSeqId(), channelEntry.lastChannelMessageSeqId())),
                                () -> clientNotificationService.sendMessage(senderSession, senderUserId, new ErrorResponse(MessageType.ENTER_REQUEST, result.getSecond().getMessage()))
                );
    }
}
