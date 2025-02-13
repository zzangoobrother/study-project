package com.example.repository;

import com.example.model.MessageDevice;
import com.example.model.constant.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageDeviceRepository extends JpaRepository<MessageDevice, Long> {
    List<MessageDevice> findAllByMessageStatusAndRetryCountLessThan(MessageStatus messageStatus, int replyCount);
}
