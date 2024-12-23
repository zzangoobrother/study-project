package com.example.controller;

import com.example.aop.LoginCheck;
import com.example.dto.CommentDto;
import com.example.dto.PostDto;
import com.example.dto.TagDto;
import com.example.dto.UserDTO;
import com.example.dto.response.CommonResponse;
import com.example.service.PostService;
import com.example.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@RequestMapping("/posts")
@RestController
public class PostController {

    private final UserService userService;
    private final PostService postService;

    public PostController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ResponseEntity<CommonResponse<PostDto>> registerPost(String accountId, @RequestBody PostDto postDto) {
        postService.register(accountId, postDto);
        CommonResponse<PostDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "registerPost", postDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @GetMapping("/my-posts")
    public ResponseEntity<CommonResponse<List<PostDto>>> myPostInfo(String accountId) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        List<PostDto> postDtoList = postService.getMyPosts(userInfo.id());
        CommonResponse<List<PostDto>> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "myPostInfo", postDtoList);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @PatchMapping("/{postId}")
    public ResponseEntity<CommonResponse<PostDto>> updatePost(String accountId, @PathVariable int postId, @RequestBody PostRequest request) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        PostDto postDto = PostDto.builder()
                .id(postId)
                .name(request.name())
                .contents(request.contents())
                .views(request.views())
                .categoryId(request.categoryId())
                .userId(userInfo.id())
                .fileId(request.fileId())
                .updateTime(request.updateTime())
                .build();

        postService.updatePosts(postDto);
        CommonResponse<PostDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "updatePost", postDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @DeleteMapping("/{postId}")
    public ResponseEntity<CommonResponse<PostDeleteRequest>> deletePosts(String accountId, @PathVariable int postId, @RequestBody PostDeleteRequest request) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        postService.deletePosts(userInfo.id(), postId);
        CommonResponse<PostDeleteRequest> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "deletePosts", request);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/comments")
    public ResponseEntity<CommonResponse<CommentDto>> registerPostComment(String accountId, @RequestBody CommentDto commentDto) {
        postService.registerComment(commentDto);
        CommonResponse<CommentDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "registerPostComment", commentDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @PatchMapping("/comments/{commentId}")
    public ResponseEntity<CommonResponse<CommentDto>> updatePostComment(String accountId, @PathVariable int commentId, @RequestBody CommentDto commentDto) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        if (userInfo != null) {
            postService.updateComment(commentDto);
        }

        CommonResponse<CommentDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "updatePostComment", commentDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<CommonResponse<CommentDto>> deletePostComment(String accountId, @PathVariable int commentId, @RequestBody CommentDto commentDto) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        if (userInfo != null) {
            postService.deletePostComment(userInfo.id(), commentId);
        }

        CommonResponse<CommentDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "deletePostComment", commentDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/tags")
    public ResponseEntity<CommonResponse<TagDto>> registerTag(String accountId, @RequestBody TagDto tagDto) {
        postService.registerTag(tagDto);
        CommonResponse<TagDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "registerTag", tagDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @PatchMapping("/tags/{tagId}")
    public ResponseEntity<CommonResponse<TagDto>> updateTag(String accountId, @PathVariable int tagId, @RequestBody TagDto tagDto) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        if (userInfo != null) {
            postService.updateTag(tagDto);
        }

        CommonResponse<TagDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "updateTag", tagDto);
        return ResponseEntity.ok(commonResponse);
    }

    @LoginCheck(type = LoginCheck.UserType.USER)
    @DeleteMapping("/tags/{tagId}")
    public ResponseEntity<CommonResponse<TagDto>> deleteTag(String accountId, @PathVariable int tagId, @RequestBody TagDto tagDto) {
        UserDTO userInfo = userService.getUserInfo(accountId);
        if (userInfo != null) {
            postService.deletePostTag(userInfo.id(), tagId);
        }

        CommonResponse<TagDto> commonResponse = new CommonResponse(HttpStatus.OK, "SUCCESS", "deleteTag", tagDto);
        return ResponseEntity.ok(commonResponse);
    }

    private record PostRequest(
            String name,
            String contents,
            int views,
            int categoryId,
            int userId,
            int fileId,
            Date updateTime
    ) {}

    private record PostDeleteRequest(
            String id,
            String accountId
    ) {}
}
