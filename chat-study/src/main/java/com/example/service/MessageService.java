package com.example.service;

import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.entity.MessageEntity;
import com.example.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final ChannelService channelService;
    private final MessageRepository messageRepository;

    public MessageService(ChannelService channelService, MessageRepository messageRepository) {
        this.channelService = channelService;
        this.messageRepository = messageRepository;
    }

    public void sendMessage(UserId senderUserId, String content, ChannelId channelId, Consumer<UserId> messageSender) {
        try {
            messageRepository.save(new MessageEntity(senderUserId.id(), content));
        } catch (Exception ex) {
            log.error("Send message failed. cause : {}", ex.getMessage());
            return;
        }

        List<UserId> participantIds = channelService.getParticipantIds(channelId);
        participantIds.stream()
                .filter(userId -> !senderUserId.equals(userId))
                .forEach(participantId -> {
                    if (channelService.isOnline(participantId, channelId)) {
                        messageSender.accept(participantId);
                    }
                });
    }
}
