package com.example.fastcampusmysql.application.usecase;

import com.example.fastcampusmysql.domain.follow.entity.Follow;
import com.example.fastcampusmysql.domain.follow.service.FollowReadService;
import com.example.fastcampusmysql.domain.post.dto.PostCommand;
import com.example.fastcampusmysql.domain.post.service.PostWriteService;
import com.example.fastcampusmysql.domain.post.service.TimelineWriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatePostUsecase {
    private final PostWriteService postWriteService;
    private final FollowReadService followReadService;
    private final TimelineWriteService timelineWriteService;

    public CreatePostUsecase(PostWriteService postWriteService, FollowReadService followReadService, TimelineWriteService timelineWriteService) {
        this.postWriteService = postWriteService;
        this.followReadService = followReadService;
        this.timelineWriteService = timelineWriteService;
    }

    @Transactional
    public Long execute(PostCommand postCommand) {
        var postId = postWriteService.create(postCommand);

        var followerMemberIds = followReadService.getFollowers(postCommand.memberId()).stream()
                .map(Follow::getFromMemberId)
                .toList();
        timelineWriteService.deliveryToTimeline(postId, followerMemberIds);

        return postId;
    }
}
