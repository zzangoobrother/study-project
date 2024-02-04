package com.example.fastcampusmysql.application.usecase;

import com.example.fastcampusmysql.domain.follow.entity.Follow;
import com.example.fastcampusmysql.domain.follow.service.FollowReadService;
import com.example.fastcampusmysql.domain.member.dto.MemberDto;
import com.example.fastcampusmysql.domain.member.service.MemberReadService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GetFollowingMemberUsecase {

    private final MemberReadService memberReadService;
    private final FollowReadService followReadService;

    public GetFollowingMemberUsecase(MemberReadService memberReadService, FollowReadService followReadService) {
        this.memberReadService = memberReadService;
        this.followReadService = followReadService;
    }

    public List<MemberDto> execute(Long memberId) {
        var following = followReadService.getFollowings(memberId);
        var followingMemberIds = following.stream().map(Follow::getToMemberId).toList();
        return memberReadService.getMembers(followingMemberIds);
    }
}
