package com.example.fastcampusmysql.application.controller;

import com.example.fastcampusmysql.domain.post.dto.DailyPostCount;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCountRequest;
import com.example.fastcampusmysql.domain.post.dto.PostCommand;
import com.example.fastcampusmysql.domain.post.service.PostReadService;
import com.example.fastcampusmysql.domain.post.service.PostWriteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/posts")
@RestController
public class PostController {

    private final PostWriteService postWriteService;
    private final PostReadService postReadService;

    public PostController(PostWriteService postWriteService, PostReadService postReadService) {
        this.postWriteService = postWriteService;
        this.postReadService = postReadService;
    }

    @PostMapping
    public Long create(@RequestBody PostCommand command) {
        return postWriteService.create(command);
    }

    @GetMapping("/daily-post-count")
    public List<DailyPostCount> getDailyPostCounts(@RequestBody DailyPostCountRequest request) {
        return postReadService.getDailyPostCount(request);
    }
}
