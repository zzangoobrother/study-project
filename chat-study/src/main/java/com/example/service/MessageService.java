package com.example.service;

import com.example.constants.MessageType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.outbound.MessageNotificationRecord;
import com.example.dto.websocket.outbound.BaseMessage;
import com.example.entity.MessageEntity;
import com.example.repository.MessageRepository;
import com.example.repository.UserChannelRepository;
import com.example.session.WebSocketSessionManager;
import com.example.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final int THREAD_POOL_SIZE = 10;

    private final ChannelService channelService;
    private final PushService pushService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final JsonUtil jsonUtil;
    private final MessageRepository messageRepository;
    private final UserChannelRepository userChannelRepository;
    private final ExecutorService senderThreadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public MessageService(ChannelService channelService, PushService pushService, WebSocketSessionManager webSocketSessionManager, JsonUtil jsonUtil, MessageRepository messageRepository, UserChannelRepository userChannelRepository) {
        this.channelService = channelService;
        this.pushService = pushService;
        this.webSocketSessionManager = webSocketSessionManager;
        this.jsonUtil = jsonUtil;
        this.messageRepository = messageRepository;
        this.userChannelRepository = userChannelRepository;

        pushService.registerPushMessageType(MessageType.NOTIFY_MESSAGE, MessageNotificationRecord.class);
    }

    @Transactional
    public void sendMessage(UserId senderUserId, String content, ChannelId channelId, MessageSeqId messageSeqId, Long serial, BaseMessage message) {
        Optional<String> json = jsonUtil.toJson(message);
        if (json.isEmpty()) {
            log.error("Send message failed. messageType : {}", message.getType());
            return;
        }
        String payload = json.get();

        try {
            messageRepository.save(new MessageEntity(channelId.id(), messageSeqId.id(), senderUserId.id(), content));
        } catch (Exception ex) {
            log.error("Send message failed. cause : {}", ex.getMessage());
            return;
        }

        List<UserId> allParticipantIds = channelService.getParticipantIds(channelId);
        List<UserId> onlineParticipantIds = channelService.getOnlineParticipantIds(channelId, allParticipantIds);

        for (int idx = 0; idx < allParticipantIds.size(); idx++) {
            UserId participantId = allParticipantIds.get(idx);
            if (senderUserId.equals(participantId)) {
                continue;
            }
            if (onlineParticipantIds.get(idx) != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        WebSocketSession session = webSocketSessionManager.getSession(participantId);
                        if (session != null) {
                            webSocketSessionManager.sendMessage(session, payload);
                        } else {
                            pushService.pushMessage(participantId, MessageType.NOTIFY_MESSAGE, payload);
                        }
                    } catch (Exception ex) {
                        pushService.pushMessage(participantId, MessageType.NOTIFY_MESSAGE, payload);
                    }
                }, senderThreadPool);
            } else {
                pushService.pushMessage(participantId, MessageType.NOTIFY_MESSAGE, payload);
            }
        }
    }

    @Transactional
    public void updateLastReadMsgSeq(UserId userId, ChannelId channelId, MessageSeqId messageSeqId) {
        if (userChannelRepository.updateLastReadMsgSeqByUserIdAndChannelId(userId.id(), channelId.id(), messageSeqId.id()) == 0) {
            log.error("Update LastReadMsgSeq failed. No record found for UserId : {} and ChannelId : {}", userId.id(), channelId.id());
        }
    }
}
