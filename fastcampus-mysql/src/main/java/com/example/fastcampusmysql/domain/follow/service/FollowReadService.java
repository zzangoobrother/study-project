package com.example.fastcampusmysql.domain.follow.service;

import com.example.fastcampusmysql.domain.follow.entity.Follow;
import com.example.fastcampusmysql.domain.follow.repository.FollowRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FollowReadService {

    private final FollowRepository followRepository;

    public FollowReadService(FollowRepository followRepository) {
        this.followRepository = followRepository;
    }

    public List<Follow> getFollowings(Long memberId) {
        return followRepository.findAllByFromMemberId(memberId);
    }

    public List<Follow> getFollowers(Long memberId) {
        return followRepository.findAllByToMemberId(memberId);
    }
}
