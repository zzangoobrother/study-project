package com.example.handler.websocket;

import com.example.constants.IdKey;
import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.websocket.inbound.LeaveRequest;
import com.example.dto.websocket.outbound.ErrorResponse;
import com.example.dto.websocket.outbound.LeaveResponse;
import com.example.service.ChannelService;
import com.example.session.WebSocketSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class LeaveRequestHandler implements BaseRequestHandler<LeaveRequest> {

    private final ChannelService channelService;
    private final WebSocketSessionManager webSocketSessionManager;

    public LeaveRequestHandler(ChannelService channelService, WebSocketSessionManager webSocketSessionManager) {
        this.channelService = channelService;
        this.webSocketSessionManager = webSocketSessionManager;
    }

    @Override
    public void handleRequest(WebSocketSession senderSession, LeaveRequest request) {
        UserId senderUserId = (UserId)senderSession.getAttributes().get(IdKey.USER_ID.getValue());

        if (channelService.leave(senderUserId)) {
            webSocketSessionManager.sendMessage(senderSession, new LeaveResponse());
        } else {
            webSocketSessionManager.sendMessage(senderSession, new ErrorResponse(MessageType.LEAVE_REQUEST, "Leave failed."));
        }
    }
}
