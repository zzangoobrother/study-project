package com.example.service.impl;

import com.example.dto.CommentDto;
import com.example.dto.PostDto;
import com.example.dto.TagDto;
import com.example.dto.UserDTO;
import com.example.mapper.CommentMapper;
import com.example.mapper.PostMapper;
import com.example.mapper.TagMapper;
import com.example.mapper.UserProfileMapper;
import com.example.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private final UserProfileMapper userProfileMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final TagMapper tagMapper;

    public PostServiceImpl(UserProfileMapper userProfileMapper, PostMapper postMapper, CommentMapper commentMapper, TagMapper tagMapper) {
        this.userProfileMapper = userProfileMapper;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.tagMapper = tagMapper;
    }

    @Override
    public void register(String id, PostDto postDto) {
        UserDTO memberInfo = userProfileMapper.getUserProfile(id);
        postDto.setUserId(memberInfo.id());
        postDto.setCreateTime(new Date());

        if (memberInfo != null) {
            postMapper.register(postDto);
            Integer postId = postDto.id();
            for (int i = 0; i < postDto.tagDtos().size(); i++) {
                TagDto tagDto = postDto.tagDtos().get(i);
                tagMapper.register(tagDto);
                int tagId = tagDto.id();
                tagMapper.createPostTag(tagId, postId);
            }
        } else {
            log.error("register ERROR : {}", postDto);
            throw new RuntimeException("register Error 게시글 등록 메서드를 확인해주세요." + postDto);
        }
    }

    @Override
    public List<PostDto> getMyPosts(int accountId) {
        List<PostDto> postDtos = postMapper.selectMyPosts(accountId);
        return postDtos;
    }

    @Override
    public void updatePosts(PostDto postDto) {
        if (postDto != null && postDto.id() != 0) {
            postMapper.updatePosts(postDto);
        } else {
            log.error("update ERROR : {}", postDto);
            throw new RuntimeException("update Error 게시글 수정 메서드를 확인해주세요." + postDto);
        }
    }

    @Override
    public void deletePosts(int userId, int postId) {
        if (userId != 0 && postId != 0) {
            postMapper.deletePosts(postId);
        } else {
            log.error("delete ERROR : {}", postId);
            throw new RuntimeException("delete Error 게시글 삭제 메서드를 확인해주세요." + postId);
        }
    }

    @Override
    public void registerComment(CommentDto commentDto) {
        if (commentDto.postId() != 0) {
            commentMapper.register(commentDto);
        } else {
            log.error("registerComment {}", commentDto);
            throw new RuntimeException("registerComment " + commentDto);
        }
    }

    @Override
    public void updateComment(CommentDto commentDto) {
        if (commentDto != null) {
            commentMapper.updateComments(commentDto);
        } else {
            log.error("updateComment {}", commentDto);
            throw new RuntimeException("updateComment " + commentDto);
        }
    }

    @Override
    public void deletePostComment(int userId, int commentId) {
        if (userId != 0 && commentId != 0) {
            commentMapper.deletePostComment(commentId);
        } else {
            log.error("deletePostComment ERROR!!");
            throw new RuntimeException("deletePostComment " + commentId);
        }
    }

    @Override
    public void registerTag(TagDto tagDto) {
        if (tagDto != null) {
            tagMapper.register(tagDto);
        } else {
            log.error("registerTag ERROR!! {}", tagDto);
            throw new RuntimeException("registerTag " + tagDto);
        }
    }

    @Override
    public void updateTag(TagDto tagDto) {
        if (tagDto != null) {
            tagMapper.updateTags(tagDto);
        } else {
            log.error("updateTag ERROR!! {}", tagDto);
            throw new RuntimeException("updateTag " + tagDto);
        }
    }

    @Override
    public void deletePostTag(int userId, int tagId) {
        if (userId != 0 && tagId != 0) {
            tagMapper.deletePostTag(tagId);
        } else {
            log.error("deletePostTag ERROR!! {}", tagId);
            throw new RuntimeException("deletePostTag " + tagId);
        }
    }
}
