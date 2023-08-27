package com.example.testcodewitharchitecture.post.controller.response;

import com.example.testcodewitharchitecture.user.controller.response.UserResponse;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostResponse {

    private Long id;
    private String content;
    private Long createdAt;
    private Long modifiedAt;
    private UserResponse writer;
}
