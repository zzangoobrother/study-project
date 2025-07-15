package com.example.service;

import com.example.constants.MessageType;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.*;
import com.example.dto.websocket.outbound.BaseMessage;
import com.example.session.WebSocketSessionManager;
import com.example.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

@Service
public class ClientNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ClientNotificationService.class);

    private final WebSocketSessionManager webSocketSessionManager;
    private final PushService pushService;
    private final JsonUtil jsonUtil;

    public ClientNotificationService(WebSocketSessionManager webSocketSessionManager, PushService pushService, JsonUtil jsonUtil) {
        this.webSocketSessionManager = webSocketSessionManager;
        this.pushService = pushService;
        this.jsonUtil = jsonUtil;

        pushService.registerPushMessageType(MessageType.INVITE_RESPONSE, InviteResponseRecord.class);
        pushService.registerPushMessageType(MessageType.ASK_INVITE, InviteNotificationRecord.class);
        pushService.registerPushMessageType(MessageType.ACCEPT_RESPONSE, AcceptResponseRecord.class);
        pushService.registerPushMessageType(MessageType.NOTIFY_ACCEPT, AcceptNotificationRecord.class);
        pushService.registerPushMessageType(MessageType.NOTIFY_JOIN, JoinNotificationRecord.class);
        pushService.registerPushMessageType(MessageType.DISCONNECT_RESPONSE, DisconnectResponseRecord.class);
        pushService.registerPushMessageType(MessageType.REJECT_RESPONSE, RejectResponseRecord.class);
        pushService.registerPushMessageType(MessageType.CREATE_RESPONSE, CreateResponseRecord.class);
        pushService.registerPushMessageType(MessageType.QUIT_RESPONSE, QuitResponseRecord.class);
    }

    public void sendErrorMessage(WebSocketSession session, BaseMessage message) {
        sendPayload(session, message, null);
    }

    public void sendMessage(UserId userId, BaseMessage message, RecordInterface recordInterface) {
        sendPayload(webSocketSessionManager.getSession(userId), message, recordInterface);
    }

    private void sendPayload(WebSocketSession session, BaseMessage message, RecordInterface recordInterface) {
        Optional<String> json = jsonUtil.toJson(message);
        if (json.isEmpty()) {
            log.error("Send message failed. messageType : {}", message.getType());
            return;
        }

        String payload = json.get();

        Runnable pushMessate = () -> {
            if (recordInterface != null) {
                pushService.pushMessage(recordInterface);
            }
        };

        try {
            if (session != null) {
                webSocketSessionManager.sendMessage(session, payload);
            } else {
                pushMessate.run();
            }
        } catch (Exception ex) {
            pushMessate.run();
        }
    }
}
