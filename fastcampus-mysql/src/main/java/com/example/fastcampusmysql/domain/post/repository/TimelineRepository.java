package com.example.fastcampusmysql.domain.post.repository;

import com.example.fastcampusmysql.domain.post.entity.Post;
import com.example.fastcampusmysql.domain.post.entity.Timeline;
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
import java.util.List;

@RequiredArgsConstructor
@Repository
public class TimelineRepository {
    private final static String TABLE = "timeline";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Timeline> rowMapper = (ResultSet resultSet, int rowNum) -> Timeline.builder()
            .id(resultSet.getLong("id"))
            .memberId(resultSet.getLong("memberId"))
            .postId(resultSet.getLong("postId"))
            .createdAt(resultSet.getObject("createdAt", LocalDateTime.class))
            .build();

    public List<Timeline> findAllByMemberIdOrderByIdDesc(Long memberId, int size) {
        var sql = String.format("""
                select *
                from %s
                where memberId = :memberId
                order by  id desc
                limit :size
                """, TABLE);

        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("size", size);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public List<Timeline> findAllByLessThanMemberIdAndOrderByIdDesc(Long id, Long memberId, int size) {
        var sql = String.format("""
                select *
                from %s
                where memberId = :memberId and id < :id
                order by  id desc
                limit :size
                """, TABLE);

        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("id", id)
                .addValue("size", size);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public Timeline save(Timeline timeline) {
        if (timeline.getId() == null) {
            return insert(timeline);
        }

        throw new UnsupportedOperationException("Timeline는 갱신을 지원하지 않습니다.");
    }

    public void bulkInsert(List<Timeline> timelines) {
        var sql = String.format("insert into `%s` (memberId, postId, createdAt) values (:memberId, :postId, :createdAt)", TABLE);

        SqlParameterSource[] params = timelines.stream()
                .map(BeanPropertySqlParameterSource::new)
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    private Timeline insert(Timeline timeline) {
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("id");

        SqlParameterSource params = new BeanPropertySqlParameterSource(timeline);
        var memberId = simpleJdbcInsert.executeAndReturnKey(params).longValue();

        return Timeline.builder()
                .id(memberId)
                .memberId(timeline.getMemberId())
                .postId(timeline.getPostId())
                .createdAt(timeline.getCreatedAt())
                .build();
    }
}
