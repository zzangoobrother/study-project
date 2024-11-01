package com.example.service.impl;

import com.example.dto.PostDto;
import com.example.dto.UserDTO;
import com.example.mapper.PostMapper;
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

    public PostServiceImpl(UserProfileMapper userProfileMapper, PostMapper postMapper) {
        this.userProfileMapper = userProfileMapper;
        this.postMapper = postMapper;
    }

    @Override
    public void register(String id, PostDto postDto) {
        UserDTO memberInfo = userProfileMapper.getUserProfile(id);
        postDto.setUserId(memberInfo.id());
        postDto.setCreateTime(new Date());

        if (memberInfo != null) {
            postMapper.register(postDto);
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
}
