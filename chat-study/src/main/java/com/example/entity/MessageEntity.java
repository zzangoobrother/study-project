package com.example.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Table(name = "message")
@Entity
public class MessageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_sequence")
    private Long messageSequence;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", nullable = false)
    private String content;

    public MessageEntity() {}

    public MessageEntity(Long userId, String content) {
        this.userId = userId;
        this.content = content;
    }

    public Long getMessageSequence() {
        return messageSequence;
    }

    public Long getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MessageEntity that = (MessageEntity)o;
        return Objects.equals(messageSequence, that.messageSequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageSequence);
    }

    @Override
    public String toString() {
        return "MessageEntity{" +
                "messageSequence=" + messageSequence +
                ", userId='" + userId + '\'' +
                ", content='" + content + '\'' +
                ", createdAt=" + getCreatedAt() +
                ", updatedAt=" + getUpdatedAt() +
                '}';
    }
}
