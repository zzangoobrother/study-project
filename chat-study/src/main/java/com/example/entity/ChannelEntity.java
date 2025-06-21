package com.example.entity;

import jakarta.persistence.*;

import java.util.Objects;

@Table(name = "channel")
@Entity
public class ChannelEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "channel_invite_code", nullable = false)
    private String channelInviteCode;

    @Column(name = "head_count", nullable = false)
    private int headCount;

    public ChannelEntity() {}

    public ChannelEntity(String title, int headCount) {
        this.title = title;
        this.headCount = headCount;
    }

    public Long getChannelId() {
        return channelId;
    }

    public String getTitle() {
        return title;
    }

    public String getChannelInviteCode() {
        return channelInviteCode;
    }

    public int getHeadCount() {
        return headCount;
    }

    public void setHeadCount(int headCount) {
        this.headCount = headCount;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ChannelEntity that = (ChannelEntity) object;
        return Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelId);
    }

    @Override
    public String toString() {
        return "ChannelEntity{" +
                "channelId=" + channelId +
                ", title='" + title + '\'' +
                ", channelInviteCode='" + channelInviteCode + '\'' +
                ", headCount=" + headCount +
                '}';
    }
}
