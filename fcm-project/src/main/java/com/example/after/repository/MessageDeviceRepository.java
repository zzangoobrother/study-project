package com.example.after.repository;

import com.example.after.model.MessageDevice;
import com.example.after.model.constant.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageDeviceRepository extends JpaRepository<MessageDevice, Long> {
    List<MessageDevice> findAllByMessageStatusAndRetryCountLessThan(MessageStatus messageStatus, int replyCount);
}
