package com.example.entity;

import java.util.Objects;

@Table(name = "message")
@IdClass(ChannelSequenceId.class)
@Entity
public class MessageEntity extends BaseEntity {

    @Id
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Id
    @Column(name = "message_sequence", nullable = false)
    private Long messageSequence;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content", nullable = false)
    private String content;

    public MessageEntity() {}

    public MessageEntity(Long channelId, Long messageSequence, Long userId, String content) {
        this.channelId = channelId;
        this.messageSequence = messageSequence;
        this.userId = userId;
        this.content = content;
    }

    public Long getChannelId() {
        return channelId;
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
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        MessageEntity that = (MessageEntity) object;
        return Objects.equals(channelId, that.channelId) && Objects.equals(messageSequence, that.messageSequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId, messageSequence);
    }

    @Override
    public String toString() {
        return "MessageEntity{" +
                "channelId=" + channelId +
                ", messageSequence=" + messageSequence +
                ", userId=" + userId +
                ", content='" + content + '\'' +
                '}';
    }
}
