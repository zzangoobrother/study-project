package com.example.fastcampusmysql.domain.follow.service;

import com.example.fastcampusmysql.domain.follow.entity.Follow;
import com.example.fastcampusmysql.domain.follow.repository.FollowRepository;
import com.example.fastcampusmysql.domain.member.dto.MemberDto;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Objects;

@Service
public class FollowWriteService {
    private final FollowRepository followRepository;

    public FollowWriteService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public void create(MemberDto fromMember, MemberDto toMember) {
        Assert.isTrue(!Objects.equals(fromMember.id(), toMember.id()), "From, To 회원이 동일 합니다.");

        var follow = Follow.builder()
                .fromMemberId(fromMember.id())
                .toMemberId(toMember.id())
                .build();

        followRepository.save(follow);
    }
}
