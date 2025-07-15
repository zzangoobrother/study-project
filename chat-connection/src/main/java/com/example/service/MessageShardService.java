package com.example.service;

import com.example.database.ShardContext;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.domain.UserId;
import com.example.dto.projection.MessageInfoProjection;
import com.example.entity.MessageEntity;
import com.example.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MessageShardService {

    private final MessageRepository messageRepository;

    public MessageShardService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public MessageSeqId findLastMessageSequenceByChannelId(ChannelId channelId) {
        try (ShardContext.ShardContextScope ignored = new ShardContext.ShardContextScope(channelId.id())) {
            return messageRepository.findLastMessageSequenceByChannelId(channelId.id()).map(MessageSeqId::new).orElse(new MessageSeqId(0L));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<MessageInfoProjection> findByChannelIdAndMessageSequenceBetween(ChannelId channelId, MessageSeqId startMessageSeqId, MessageSeqId endMessageSeqId) {
        try (ShardContext.ShardContextScope ignored = new ShardContext.ShardContextScope(channelId.id())) {
            return messageRepository.findByChannelIdAndMessageSequenceBetween(channelId.id(), startMessageSeqId.id(), endMessageSeqId.id());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(ChannelId channelId, MessageSeqId messageSeqId, UserId senderUserId, String content) {
        try (ShardContext.ShardContextScope ignored = new ShardContext.ShardContextScope(channelId.id())) {
            messageRepository.save(new MessageEntity(channelId.id(), messageSeqId.id(), senderUserId.id(), content));
        }
    }
}
