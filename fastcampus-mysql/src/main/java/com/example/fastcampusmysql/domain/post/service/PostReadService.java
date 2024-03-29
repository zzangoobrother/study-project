package com.example.fastcampusmysql.domain.post.service;

import com.example.fastcampusmysql.domain.post.dto.DailyPostCount;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCountRequest;
import com.example.fastcampusmysql.domain.post.dto.PostDto;
import com.example.fastcampusmysql.domain.post.entity.Post;
import com.example.fastcampusmysql.domain.post.repository.PostLikeRepository;
import com.example.fastcampusmysql.domain.post.repository.PostRepository;
import com.example.fastcampusmysql.util.CusorRequest;
import com.example.fastcampusmysql.util.PageCursor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostReadService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    public PostReadService(PostRepository postRepository, PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
    }

    public List<DailyPostCount> getDailyPostCount(DailyPostCountRequest request) {
        return postRepository.groupByCreatedDate(request);
    }

    public Page<PostDto> getPosts(Long memberId, Pageable pageable) {
        return postRepository.findAllByMemberId(memberId, pageable).map(post -> toDto(post));
    }

    private PostDto toDto(Post post) {
        return new PostDto(post.getId(), post.getContents(), post.getCreatedAt(), postLikeRepository.getCount(post.getId()));
    }

    public PageCursor<Post> getPosts(Long memberId, CusorRequest request) {
        var posts  = findAllBy(memberId, request);

        return getNextKey(posts, request);
    }

    private List<Post> findAllBy(Long memberId, CusorRequest request) {
        if (request.hasKey()) {
            return postRepository.findAllByLessThanMemberIdAndOrderByIdDesc(request.key(), memberId, request.size());
        }

        return postRepository.findAllByMemberIdAndOrderByIdDesc(memberId, request.size());
    }

    public PageCursor<Post> getPosts(List<Long> memberIds, CusorRequest request) {
        var posts  = findAllBy(memberIds, request);

        return getNextKey(posts, request);
    }

    public List<Post> getPosts(List<Long> postIds) {
        return postRepository.findAllByInId(postIds);
    }

    public Post getPosts(Long postId) {
        return postRepository.findById(postId, false).orElseThrow();
    }

    private List<Post> findAllBy(List<Long> memberIds, CusorRequest request) {
        if (request.hasKey()) {
            return postRepository.findAllByLessThanMemberIdAndOrderByIdDesc(request.key(), memberIds, request.size());
        }

        return postRepository.findAllByMemberIdAndOrderByIdDesc(memberIds, request.size());
    }

    private PageCursor<Post> getNextKey(List<Post> posts, CusorRequest request) {
        var nextKey = posts.stream()
                .mapToLong(post -> post.getId())
                .min()
                .orElse(CusorRequest.NONE_KEY);

        return new PageCursor<>(request.next(nextKey), posts);
    }
}
