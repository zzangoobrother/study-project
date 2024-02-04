package com.example.fastcampusmysql.domain.member.repository;

import com.example.fastcampusmysql.domain.member.entity.Member;
import com.example.fastcampusmysql.domain.member.entity.MemberNicknameHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class MemberRepository {
    private final static String TABLE = "member";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    static final RowMapper<Member> rowMapper = (ResultSet resultSet, int rowNum) -> Member.builder()
            .id(resultSet.getLong("id"))
            .email(resultSet.getString("email"))
            .nickname(resultSet.getString("nickname"))
            .birthday(resultSet.getObject("birthday", LocalDate.class))
            .createdAt(resultSet.getObject("createdAt", LocalDateTime.class))
            .build();

    public Optional<Member> findById(Long id) {
        String sql = String.format("SELECT * FROM %s WHERE id = :id", TABLE);
        var param = new MapSqlParameterSource()
                .addValue("id", id);

        var member = jdbcTemplate.queryForObject(sql, param, rowMapper);
        return Optional.ofNullable(member);
    }

    public List<Member> findAllByIdIn(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        var sql = String.format("select * from %s where id in (:ids)", TABLE);
        var param = new MapSqlParameterSource().addValue("ids", ids);
        return jdbcTemplate.query(sql, param, rowMapper);
    }

    public Member save(Member member) {
        if (member.getId() == null) {
            return insert(member);
        }

        return update(member);
    }

    private Member insert(Member member) {
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("id");

        SqlParameterSource params = new BeanPropertySqlParameterSource(member);
        var memberId = simpleJdbcInsert.executeAndReturnKey(params).longValue();

        return Member.builder()
                .id(memberId)
                .email(member.getEmail())
                .nickname(member.getNickname())
                .birthday(member.getBirthday())
                .createdAt(member.getCreatedAt())
                .build();
    }

    private Member update(Member member) {
        var sql = String.format("update %s set email = :email, nickname = :nickname, birthday = :birthday where id = :id", TABLE);
        SqlParameterSource params = new BeanPropertySqlParameterSource(member);
        jdbcTemplate.update(sql, params);
        return member;
    }
}
