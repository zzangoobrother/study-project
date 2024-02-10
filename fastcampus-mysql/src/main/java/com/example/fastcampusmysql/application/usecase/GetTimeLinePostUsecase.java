package com.example.fastcampusmysql.application.usecase;

import com.example.fastcampusmysql.domain.follow.entity.Follow;
import com.example.fastcampusmysql.domain.follow.service.FollowReadService;
import com.example.fastcampusmysql.domain.post.entity.Post;
import com.example.fastcampusmysql.domain.post.entity.Timeline;
import com.example.fastcampusmysql.domain.post.service.PostReadService;
import com.example.fastcampusmysql.domain.post.service.TimelineReadService;
import com.example.fastcampusmysql.util.CusorRequest;
import com.example.fastcampusmysql.util.PageCursor;
import org.springframework.stereotype.Service;

@Service
public class GetTimeLinePostUsecase {

    private final FollowReadService followReadService;
    private final PostReadService postReadService;
    private final TimelineReadService timelineReadService;

    public GetTimeLinePostUsecase(FollowReadService followReadService, PostReadService postReadService, TimelineReadService timelineReadService) {
        this.followReadService = followReadService;
        this.postReadService = postReadService;
        this.timelineReadService = timelineReadService;
    }

    public PageCursor<Post> execute(Long memberId, CusorRequest cusorRequest) {
        var followings = followReadService.getFollowings(memberId);
        var followingMemberIds = followings.stream()
                .map(Follow::getToMemberId)
                .toList();

        return postReadService.getPosts(followingMemberIds, cusorRequest);
    }

    public PageCursor<Post> executeByTimeline(Long memberId, CusorRequest cusorRequest) {
        var timelines = timelineReadService.getTimelines(memberId, cusorRequest);
        var postIds = timelines.body().stream()
                .map(Timeline::getPostId)
                .toList();

        var posts = postReadService.getPosts(postIds);

        return new PageCursor(timelines.nextCursorRequest(), posts);
    }
}
