package com.example.service;

import com.example.constants.KeyPrefix;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.MessageSeqId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class MessageSeqIdGenerator {
    private static final Logger log = LoggerFactory.getLogger(MessageSeqIdGenerator.class);

    private final StringRedisTemplate stringRedisTemplate;

    public MessageSeqIdGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Optional<MessageSeqId> getNext(ChannelId channelId) {
        String key = buildMessageSeqIdKey(channelId.id());
        try {
            return Optional.of(new MessageSeqId(stringRedisTemplate.opsForValue().increment(key)));
        } catch (Exception ex) {
            log.error("Redis set failed key : {}, cause : {}", key, ex.getMessage());
        }

        return Optional.empty();
    }

    private String buildMessageSeqIdKey(Long channelId) {
        return "%s:%d:seq_id".formatted(KeyPrefix.CHANNEL, channelId);
    }
}
