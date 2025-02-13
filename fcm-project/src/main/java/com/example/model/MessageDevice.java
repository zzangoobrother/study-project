package com.example.model;

import com.example.model.constant.MessageStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class MessageDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "device_id")
    private Long deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private MessageStatus messageStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Builder
    public MessageDevice(Long messageId, Long deviceId, MessageStatus messageStatus, int retryCount) {
        this.messageId = messageId;
        this.deviceId = deviceId;
        this.messageStatus = messageStatus;
        this.retryCount = retryCount;
    }
}

