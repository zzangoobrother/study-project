package com.example.fastcampusmysql.application.controller;

import com.example.fastcampusmysql.application.usecase.CreatePostUsecase;
import com.example.fastcampusmysql.application.usecase.GetTimeLinePostUsecase;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCount;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCountRequest;
import com.example.fastcampusmysql.domain.post.dto.PostCommand;
import com.example.fastcampusmysql.domain.post.entity.Post;
import com.example.fastcampusmysql.domain.post.service.PostReadService;
import com.example.fastcampusmysql.domain.post.service.PostWriteService;
import com.example.fastcampusmysql.util.CusorRequest;
import com.example.fastcampusmysql.util.PageCursor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/posts")
@RestController
public class PostController {

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;
    private final GetTimeLinePostUsecase getTimeLinePostUsecase;
    private final CreatePostUsecase createPostUsecase;

    public PostController(PostWriteService postWriteService, PostReadService postReadService, GetTimeLinePostUsecase getTimeLinePostUsecase, CreatePostUsecase createPostUsecase) {
        this.postWriteService = postWriteService;
        this.postReadService = postReadService;
        this.getTimeLinePostUsecase = getTimeLinePostUsecase;
        this.createPostUsecase = createPostUsecase;
    }

    @PostMapping
    public Long create(@RequestBody PostCommand command) {
        return createPostUsecase.execute(command);
    }

    @GetMapping("/daily-post-count")
    public List<DailyPostCount> getDailyPostCounts(@RequestBody DailyPostCountRequest request) {
        return postReadService.getDailyPostCount(request);
    }

    @GetMapping("/members/{memberId}")
    public Page<Post> getPosts(@PathVariable Long memberId, Pageable pageable) {
        return postReadService.getPosts(memberId, pageable);
    }

    @GetMapping("/members/{memberId}/by-cursor")
    public PageCursor<Post> getPostsByCursor(@PathVariable Long memberId, @RequestBody CusorRequest request) {
        return postReadService.getPosts(memberId, request);
    }

    @GetMapping("/members/{memberId}/timeline")
    public PageCursor<Post> getTimeline(@PathVariable Long memberId, CusorRequest request) {
        return getTimeLinePostUsecase.executeByTimeline(memberId, request);
    }

    @PostMapping("/{postId}/like")
    public void likePost(Long postId) {
//        postWriteService.likePost(postId);
        postWriteService.likePostByOptimisticLock(postId);
    }
}
