package com.example.fastcampusmysql.domain.follow.repository;

import com.example.fastcampusmysql.domain.follow.entity.Follow;
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
public class FollowRepository {
    private final static String TABLE = "follow";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    static final RowMapper<Follow> rowMapper = (ResultSet resultSet, int rowNum) -> Follow.builder()
            .id(resultSet.getLong("id"))
            .fromMemberId(resultSet.getLong("fromMemberId"))
            .toMemberId(resultSet.getLong("toMemberId"))
            .createdAt(resultSet.getObject("createdAt", LocalDateTime.class))
            .build();

    public List<Follow> findAllByFromMemberId(Long fromMemberId) {
        var sql = String.format("select * from %s where fromMemberId = ?", TABLE);
        var params = new MapSqlParameterSource("fromMemberId", fromMemberId);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public List<Follow> findAllByToMemberId(Long toMemberId) {
        var sql = String.format("select * from %s where toMemberId = ?", TABLE);
        var params = new MapSqlParameterSource("toMemberId", toMemberId);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public Follow save(Follow follow) {
        if (follow.getId() == null) {
            return insert(follow);
        }

        throw new UnsupportedOperationException("Follow는 갱신을 지원하지 않습니다.");
    }

    private Follow insert(Follow follow) {
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("id");

        SqlParameterSource params = new BeanPropertySqlParameterSource(follow);
        var memberId = simpleJdbcInsert.executeAndReturnKey(params).longValue();

        return Follow.builder()
                .id(memberId)
                .fromMemberId(follow.getFromMemberId())
                .toMemberId(follow.getToMemberId())
                .createdAt(follow.getCreatedAt())
                .build();
    }
}
