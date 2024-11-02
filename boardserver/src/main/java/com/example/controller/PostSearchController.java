package com.example.controller;

import com.example.dto.PostDto;
import com.example.dto.request.PostSearchRequest;
import com.example.service.PostSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequestMapping("/search")
@RestController
public class PostSearchController {

    private final PostSearchService postSearchService;

    public PostSearchController(PostSearchService postSearchService) {
        this.postSearchService = postSearchService;
    }

    @PostMapping
    public PostSearchResponse search(@RequestBody PostSearchRequest request) {
        List<PostDto> postDtos = postSearchService.getPosts(request);
        return new PostSearchResponse(postDtos);
    }

    private record PostSearchResponse(
            List<PostDto> postDtos
    ) {}
}
