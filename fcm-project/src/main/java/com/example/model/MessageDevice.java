package com.example.model;

import com.example.model.constant.MessageStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

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

    @Column(name = "options", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> option;

    @Builder
    public MessageDevice(Long messageId, Long deviceId, MessageStatus messageStatus, int retryCount, Map<String, String> option) {
        this.messageId = messageId;
        this.deviceId = deviceId;
        this.messageStatus = messageStatus;
        this.retryCount = retryCount;
        this.option = option;
    }
}

