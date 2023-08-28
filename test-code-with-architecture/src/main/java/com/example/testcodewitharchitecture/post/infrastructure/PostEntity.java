package com.example.testcodewitharchitecture.post.infrastructure;

import com.example.testcodewitharchitecture.post.domain.Post;
import com.example.testcodewitharchitecture.user.infrastructure.UserEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "posts")
public class PostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content")
    private String content;

    @Column(name = "created_at")
    private Long createdAt;

    @Column(name = "modified_at")
    private Long modifiedAt;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserEntity writer;

    public static PostEntity fromModel(Post post) {
        PostEntity postEntity = new PostEntity();
        postEntity.setId(post.getId());
        postEntity.setContent(post.getContent());
        postEntity.setCreatedAt(post.getCreatedAt());
        postEntity.setModifiedAt(post.getModifiedAt());
        postEntity.setWriter(UserEntity.fromModel(post.getWriter()));
        return postEntity;
    }

    public Post toModel() {
        return Post.builder()
                .id(id)
                .content(content)
                .createdAt(createdAt)
                .modifiedAt(modifiedAt)
                .writer(writer.toModel())
                .build();
    }
}
