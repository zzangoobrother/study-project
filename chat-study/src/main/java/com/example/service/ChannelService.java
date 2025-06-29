package com.example.service;

import com.example.constants.ResultType;
import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.Channel;
import com.example.dto.domain.ChannelId;
import com.example.dto.domain.InviteCode;
import com.example.dto.domain.UserId;
import com.example.dto.projection.ChannelTitleProjection;
import com.example.entity.ChannelEntity;
import com.example.entity.UserChannelEntity;
import com.example.repository.ChannelRepository;
import com.example.repository.UserChannelRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(UserConnectionService.class);
    private static final int LIMIT_HEAD_COUNT = 100;

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

    @Transactional(readOnly = true)
    public Optional<InviteCode> getInviteCode(ChannelId channelId) {
        Optional<InviteCode> inviteCode = channelRepository.findChannelInviteCodeByChannelId(channelId.id())
                .map(projection -> new InviteCode(projection.getInviteCode()));

        if (inviteCode.isEmpty()) {
            log.warn("Invite code is not exist. ChannelId : {}", channelId);
        }

        return inviteCode;
    }

    @Transactional(readOnly = true)
    public boolean isJoined(ChannelId channelId, UserId userId) {
        return userChannelRepository.existsByUserIdAndChannelId(userId.id(), channelId.id());
    }

    @Transactional(readOnly = true)
    public List<UserId> getParticipantIds(ChannelId channelId) {
        return userChannelRepository.findUserIdByChannelId(channelId.id()).stream()
                .map(userId -> new UserId(userId.getUserId()))
                .toList();
    }

    public List<UserId> getOnlineParticipantIds(ChannelId channelId, List<UserId> userIds) {
        return sessionService.getOnlineParticipantUSerIds(channelId, userIds);
    }

    @Transactional(readOnly = true)
    public Optional<Channel> getChannel(InviteCode inviteCode) {
        return channelRepository.findChannelByInviteCode(inviteCode.code())
                .map(projection -> new Channel(new ChannelId(projection.getChannelId()), projection.getTitle(), projection.getHeadCount()));
    }

    @Transactional(readOnly = true)
    public List<Channel> getChannels(UserId userId) {
        return userChannelRepository.findChannelsByUserId(userId.id()).stream()
                .map(projection -> new Channel(new ChannelId(projection.getChannelId()), projection.getTitle(), projection.getHeadCount()))
                .toList();
    }

    @Transactional
    public Pair<Optional<Channel>, ResultType> create(UserId senderUserId, List<UserId> participantIds, String title) {
        if (title == null || title.isEmpty()) {
            log.warn("Invalid args : title is empty.");
            return Pair.of(Optional.empty(), ResultType.INVALID_ARGS);
        }

        int headCount = participantIds.size() + 1;
        if (headCount > LIMIT_HEAD_COUNT) {
            log.warn("Over limit of channel. senderUserId : {}, participantIds count : {}, title : {}", senderUserId, participantIds.size(), title);
            return Pair.of(Optional.empty(), ResultType.OVER_LIMIT);
        }

        if (userConnectionService.countConnectionStatus(senderUserId, participantIds, UserConnectionStatus.ACCEPTED) != participantIds.size()) {
            log.warn("Included Unconnected user. participantIds : {}", participantIds);
            return Pair.of(Optional.empty(), ResultType.NOT_ALLOWED);
        }

        try {
            ChannelEntity channelEntity = channelRepository.save(new ChannelEntity(title, headCount));
            Long channelId = channelEntity.getChannelId();
            List<UserChannelEntity> userChannelEntities = participantIds.stream().map(participantId -> new UserChannelEntity(participantId.id(), channelId, 0)).collect(Collectors.toList());
            userChannelEntities.add(new UserChannelEntity(senderUserId.id(), channelId, 0));
            userChannelRepository.saveAll(userChannelEntities);
            Channel channel = new Channel(new ChannelId(channelId), title, headCount);
            return Pair.of(Optional.of(channel), ResultType.SUCCESS);
        } catch (Exception ex) {
            log.error("Create failed. cause : {}", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Pair<Optional<Channel>, ResultType> join(InviteCode inviteCode, UserId userId) {
        Optional<Channel> ch = getChannel(inviteCode);
        if (ch.isEmpty()) {
            return Pair.of(Optional.empty(), ResultType.NOT_FOUND);
        }

        Channel channel = ch.get();
        if (isJoined(channel.channelId(), userId)) {
            return Pair.of(Optional.empty(), ResultType.ALREADY_JOINED);
        } else if (channel.headCount() >= LIMIT_HEAD_COUNT) {
            return Pair.of(Optional.empty(), ResultType.OVER_LIMIT);
        }

        ChannelEntity channelEntity = channelRepository.findForUpdateByChannelId(channel.channelId().id())
                .orElseThrow(() -> new EntityNotFoundException("Invalid channelId : " + channel.channelId()));
        if (channelEntity.getHeadCount() < LIMIT_HEAD_COUNT) {
            channelEntity.setHeadCount(channelEntity.getHeadCount() + 1);
            userChannelRepository.save(new UserChannelEntity(userId.id(), channel.channelId().id(), 0));
        }

        return Pair.of(Optional.of(channel), ResultType.SUCCESS);
    }

    @Transactional(readOnly = true)
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

    public boolean leave(UserId userId) {
        return sessionService.removeActiveChannel(userId);
    }

    @Transactional
    public ResultType quit(ChannelId channelId, UserId userId) {
        if (!isJoined(channelId, userId)) {
            return ResultType.NOT_JOINED;
        }

        ChannelEntity channelEntity = channelRepository.findForUpdateByChannelId(channelId.id())
                .orElseThrow(() -> new EntityNotFoundException("Invalid channelId : " + channelId));
        if (channelEntity.getHeadCount() > 0) {
            channelEntity.setHeadCount(channelEntity.getHeadCount() - 1);
        } else {
            log.error("Count is already zero. channelId : {}, userId : {}", channelId, userId);
        }

        userChannelRepository.deleteByUserIdAndChannelId(userId.id(), channelId.id());
        return ResultType.SUCCESS;
    }
}
