package com.example.service;

import com.example.constants.ResultType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.Channel;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.UserId;
import com.example.dto.projection.ChannelTitleProjection;
import com.example.entity.ChannelEntity;
import com.example.entity.UserChannelEntity;
import com.example.repository.ChannelRepository;
import com.example.repository.UserChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(UserConnectionService.class);

    private final SessionService sessionService;
    private final UserConnectionService userConnectionService;
    private final ChannelRepository channelRepository;
    private final UserChannelRepository userChannelRepository;

    public ChannelService(SessionService sessionService, UserConnectionService userConnectionService, ChannelRepository channelRepository, UserChannelRepository userChannelRepository) {
        this.sessionService = sessionService;
        this.userConnectionService = userConnectionService;
        this.channelRepository = channelRepository;
        this.userChannelRepository = userChannelRepository;
    }

    public boolean isJoined(ChannelId channelId, UserId userId) {
        return userChannelRepository.existsByUserIdAndChannelId(userId.id(), channelId.id());
    }

    public List<UserId> getParticipantIds(ChannelId channelId) {
        return userChannelRepository.findUserIdByChannelId(channelId.id()).stream()
                .map(userId -> new UserId(userId.getUserId()))
                .toList();
    }

    public boolean isOnline(UserId userId, ChannelId channelId) {
        return sessionService.isOnline(userId, channelId);
    }

    @Transactional
    public Pair<Optional<Channel>, ResultType> create(UserId senderUserId, UserId participantId, String title) {
        if (title == null || title.isEmpty()) {
            log.warn("Invalid args : title is empty.");
            return Pair.of(Optional.empty(), ResultType.INVALID_ARGS);
        }

        if (userConnectionService.getStatus(senderUserId, participantId) != UserConnectionStatus.ACCEPTED) {
            log.warn("Included Unconnected user. participantId : {}", participantId);
            return Pair.of(Optional.empty(), ResultType.NOT_ALLOWED);
        }

        try {
            final int HEAD_COUNT = 2;
            ChannelEntity channelEntity = channelRepository.save(new ChannelEntity(title, HEAD_COUNT));
            Long channelId = channelEntity.getChannelId();
            List<UserChannelEntity> userChannelEntities = List.of(new UserChannelEntity(senderUserId.id(), channelId, 0), new UserChannelEntity(participantId.id(), channelId, 0));
            userChannelRepository.saveAll(userChannelEntities);
            Channel channel = new Channel(new ChannelId(channelId), title, HEAD_COUNT);
            return Pair.of(Optional.of(channel), ResultType.SUCCESS);
        } catch (Exception ex) {
            log.error("Create failed. cause : {}", ex.getMessage());
            throw ex;
        }
    }

    public Pair<Optional<String>, ResultType> enter(ChannelId channelId, UserId userId) {
        if (!isJoined(channelId, userId)) {
            log.warn("Enter channel failed. User not joined the channel. channelId : {}, userId : {}", channelId, userId);
            return Pair.of(Optional.empty(), ResultType.NOT_JOINED);
        }

        Optional<String> title = channelRepository.findChannelTitleByChannelId(channelId.id()).map(ChannelTitleProjection::getTitle);
        if (title.isEmpty()) {
            log.warn("Enter channel failed. Channel does not exist channelId : {}, userId : {}", channelId, userId);
            return Pair.of(Optional.empty(), ResultType.NOT_FOUND);
        }

        if (sessionService.setActiveChannel(userId, channelId)) {
            return Pair.of(title, ResultType.SUCCESS);
        }

        log.error("Enter channel failed. channelId : {}, userId : {}", channelId, userId);
        return Pair.of(Optional.empty(), ResultType.FAILED);
    }
}
