package com.example.service;

import com.example.constants.MessageType;
import com.example.constants.ResultType;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;
import com.example.dto.kafka.MessageNotificationRecord;
import com.example.dto.kafka.WriteMessageAckRecord;
import com.example.dto.kafka.WriteMessageRecord;
import com.example.dto.projection.MessageInfoProjection;
import com.example.kafka.KafkaProducer;
import com.example.repository.UserChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final UserService userService;
    private final ChannelService channelService;
    private final PushService pushService;
    private final MessageShardService messageShardService;
    private final SessionService sessionService;
    private final KafkaProducer kafkaProducer;
    private final UserChannelRepository userChannelRepository;

    public MessageService(UserService userService, ChannelService channelService, PushService pushService, MessageShardService messageShardService, KafkaProducer kafkaProducer, SessionService sessionService, UserChannelRepository userChannelRepository) {
        this.userService = userService;
        this.channelService = channelService;
        this.pushService = pushService;
        this.messageShardService = messageShardService;
        this.sessionService = sessionService;
        this.kafkaProducer = kafkaProducer;
        this.userChannelRepository = userChannelRepository;

        pushService.registerPushMessageType(MessageType.NOTIFY_MESSAGE, MessageNotificationRecord.class);
    }

    @Transactional(readOnly = true)
    public Pair<List<Message>, ResultType> getMessages(ChannelId channelId, MessageSeqId startMessageSeqId, MessageSeqId endMessageSeqId) {
        List<MessageInfoProjection> messageInfos = messageShardService.findByChannelIdAndMessageSequenceBetween(channelId, startMessageSeqId, endMessageSeqId);
        Set<UserId> userIds = messageInfos.stream()
                .map(proj -> new UserId(proj.getUserId()))
                .collect(Collectors.toUnmodifiableSet());

        if (userIds.isEmpty()) {
            return Pair.of(Collections.emptyList(), ResultType.SUCCESS);
        }

        Pair<Map<UserId, String>, ResultType> result = userService.getUsernames(userIds);
        if (result.getSecond() == ResultType.SUCCESS) {
            List<Message> messages = messageInfos.stream()
                    .map(proj -> {
                        UserId userId = new UserId(proj.getUserId());
                        return new Message(channelId, new MessageSeqId(proj.getMessageSequence()),
                                result.getFirst().getOrDefault(userId, "unknown"), proj.getContent());
                    }).toList();

            return Pair.of(messages, ResultType.SUCCESS);
        } else {
            return Pair.of(Collections.emptyList(), result.getSecond());
        }
    }

    @Transactional
    public void sendMessage(WriteMessageRecord record) {
        ChannelId channelId = record.channelId();
        UserId senderUserId = record.userId();
        MessageSeqId messageSeqId = record.messageSeqId();
        String senderUsername = userService.getUsername(senderUserId).orElse("unknown");

        try {
            messageShardService.save(channelId, messageSeqId, senderUserId, record.content());
        } catch (Exception ex) {
            log.error("Send message failed. cause : {}", ex.getMessage());
            return;
        }

        List<UserId> allParticipantIds = channelService.getParticipantIds(channelId);
        List<UserId> onlineParticipantIds = channelService.getOnlineParticipantIds(channelId, allParticipantIds);

        Map<String, List<UserId>> listenTopics = sessionService.getListenTopics(onlineParticipantIds);
        allParticipantIds.removeAll(onlineParticipantIds);

        listenTopics.forEach((listenTopic, participantIds) -> {
            if (participantIds.contains(senderUserId)) {
                updateLastReadMsgSeq(senderUserId, channelId, messageSeqId);
                kafkaProducer.sendMessageUsingPartitionKey(listenTopic, channelId, senderUserId, new WriteMessageAckRecord(senderUserId, record.serial(), messageSeqId));
                participantIds.remove(senderUserId);
            } else {
                kafkaProducer.sendMessageUsingPartitionKey(listenTopic, channelId, senderUserId, new MessageNotificationRecord(senderUserId, channelId, messageSeqId, senderUsername, record.content(), participantIds));
            }
        });

        if (!allParticipantIds.isEmpty()) {
            pushService.pushMessage(new MessageNotificationRecord(senderUserId, channelId, messageSeqId, senderUsername, record.content(), allParticipantIds));
        }
    }

    @Transactional
    public void updateLastReadMsgSeq(UserId userId, ChannelId channelId, MessageSeqId messageSeqId) {
        if (userChannelRepository.updateLastReadMsgSeqByUserIdAndChannelId(userId.id(), channelId.id(), messageSeqId.id()) == 0) {
            log.error("Update LastReadMsgSeq failed. No record found for UserId : {} and ChannelId : {}", userId.id(), channelId.id());
        }
    }
}
