package com.example.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.model.MessageDevice;
import com.example.model.constant.MessageStatus;

import java.util.List;

public interface MessageDeviceRepository extends JpaRepository<MessageDevice, Long> {
    List<MessageDevice> findAllByMessageStatusAndRetryCountLessThan(MessageStatus messageStatus, int replyCount);

    List<MessageDevice> findByMessageId(Long messageId);

    List<MessageDevice> findByMessageIdAndMessageStatus(Long messageId, MessageStatus status);
}
