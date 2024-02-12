package com.example.fastcampusmysql.domain.post.repository;

import com.example.fastcampusmysql.domain.post.entity.PostLike;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Repository
public class PostLikeRepository {
    private final static String TABLE = "PostLike";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<PostLike> rowMapper = (ResultSet resultSet, int rowNum) -> PostLike.builder()
            .id(resultSet.getLong("id"))
            .postId(resultSet.getLong("postId"))
            .memberId(resultSet.getLong("memberId"))
            .createdAt(resultSet.getObject("createdAt", LocalDateTime.class))
            .build();

    public long getCount(Long postId) {
        var sql = String.format("""
                select count(id)
                from %s
                where postId = :postId
                """, TABLE);

        var params = new MapSqlParameterSource().addValue("postId", postId);
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    public PostLike save(PostLike postLike) {
        if (postLike.getId() == null) {
            return insert(postLike);
        }

        throw new UnsupportedOperationException("PostLike는 갱신을 지원하지 않습니다.");
    }

    private PostLike insert(PostLike postLike) {
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("id");

        SqlParameterSource params = new BeanPropertySqlParameterSource(postLike);

        return PostLike.builder()
                .id(postLike.getId())
                .postId(postLike.getPostId())
                .memberId(postLike.getMemberId())
                .createdAt(postLike.getCreatedAt())
                .build();
    }
}
