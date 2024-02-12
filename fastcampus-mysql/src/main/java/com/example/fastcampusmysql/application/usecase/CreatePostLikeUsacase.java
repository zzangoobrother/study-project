package com.example.fastcampusmysql.application.usecase;

import com.example.fastcampusmysql.domain.member.service.MemberReadService;
import com.example.fastcampusmysql.domain.post.service.PostLikeWriteService;
import com.example.fastcampusmysql.domain.post.service.PostReadService;
import org.springframework.stereotype.Service;

@Service
public class CreatePostLikeUsacase {

    private final PostLikeWriteService postLikeWriteService;
    private final MemberReadService memberReadService;
    private final PostReadService postReadService;

    public CreatePostLikeUsacase(PostLikeWriteService postLikeWriteService, MemberReadService memberReadService, PostReadService postReadService) {
        this.postLikeWriteService = postLikeWriteService;
        this.memberReadService = memberReadService;
        this.postReadService = postReadService;
    }

    public void execute(Long postId, Long memberId) {
        var post = postReadService.getPosts(postId);
        var member = memberReadService.getMember(memberId);
        postLikeWriteService.create(post, member);
    }
}
