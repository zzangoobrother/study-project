package com.example.service;

import com.example.dto.domain.ChannelId;
import com.example.dto.domain.Message;
import com.example.dto.domain.MessageSeqId;

import java.util.TreeSet;

public class UserService {

    private Location userLocation = Location.LOBBY;
    private String username = "";
    private ChannelId channelId = null;
    private volatile MessageSeqId lastReadMessageSeqId = null;
    private final TreeSet<Message> messagesBuffer = new TreeSet<>();

    public MessageSeqId getLastReadMessageSeqId() {
        return lastReadMessageSeqId;
    }

    public synchronized void setLastReadMessageSeqId(MessageSeqId lastReadMessageSeqId) {
        if (getLastReadMessageSeqId() == null || lastReadMessageSeqId == null || getLastReadMessageSeqId().id() < lastReadMessageSeqId.id()) {
            this.lastReadMessageSeqId = lastReadMessageSeqId;
        }
    }

    public boolean isInLobby() {
        return userLocation == Location.LOBBY;
    }

    public boolean isInChannel() {
        return userLocation == Location.CHANNEL;
    }

    public String getUsername() {
        return username;
    }

    public ChannelId getChannelId() {
        return channelId;
    }

    public boolean isBufferEmpty() {
        return messagesBuffer.isEmpty();
    }

    public int getBufferSize() {
        return messagesBuffer.size();
    }

    public Message peekMessage() {
        return messagesBuffer.first();
    }

    public void addMessage(Message message) {
        messagesBuffer.add(message);
    }

    public Message popMessage() {
        return messagesBuffer.pollFirst();
    }

    public void login(String username) {
        this.username = username;
        moveToLobby();
    }

    public void logout() {
        this.username = "";
        moveToLobby();
    }

    public void moveToLobby() {
        userLocation = Location.LOBBY;
        this.channelId = null;
        setLastReadMessageSeqId(null);
        messagesBuffer.clear();
    }

    public void moveToChannel(ChannelId channelId) {
        userLocation = Location.CHANNEL;
        this.channelId = channelId;
        setLastReadMessageSeqId(null);
        messagesBuffer.clear();
    }

    private enum  Location {
        LOBBY, CHANNEL;
    }
}
