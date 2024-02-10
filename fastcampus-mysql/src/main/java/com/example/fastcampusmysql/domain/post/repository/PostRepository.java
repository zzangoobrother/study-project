package com.example.fastcampusmysql.domain.post.repository;

import com.example.fastcampusmysql.domain.PageHelper;
import com.example.fastcampusmysql.domain.member.entity.Member;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCount;
import com.example.fastcampusmysql.domain.post.dto.DailyPostCountRequest;
import com.example.fastcampusmysql.domain.post.entity.Post;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

@RequiredArgsConstructor
@Repository
public class PostRepository {
    private final static String TABLE = "post";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final RowMapper<Post> rowMapper = (ResultSet resultSet, int rowNum) -> Post.builder()
            .id(resultSet.getLong("id"))
            .memberId(resultSet.getLong("memberId"))
            .contents(resultSet.getString("contents"))
            .createdDate(resultSet.getObject("createdDate", LocalDate.class))
            .createdAt(resultSet.getObject("createdAt", LocalDateTime.class))
            .build();

    static final RowMapper<DailyPostCount> DAILY_POST_COUNT_ROW_MAPPER =
            (ResultSet resultSet, int rowNum) ->
                    new DailyPostCount(resultSet.getLong("memberId"), resultSet.getObject("createdDate", LocalDate.class), resultSet.getLong("count"));

    public List<DailyPostCount> groupByCreatedDate(DailyPostCountRequest request) {
        var sql = String.format("""
                select createdDate, memberId, count(id) as count
                from %s
                where memberId = :memberId
                and createdDate between :firstDate and :lastDate
                group by memberId
                """, TABLE);

        var params = new BeanPropertySqlParameterSource(request);
        return jdbcTemplate.query(sql, params, DAILY_POST_COUNT_ROW_MAPPER);
    }

    public Page<Post> findAllByMemberId(Long memberId, Pageable pageable) {
        var params = new MapSqlParameterSource()
                .addValue("memberId", memberId)
                .addValue("size", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        var sql = String.format("""
                select *
                from %s
                where memberId = :memberId
                order by %s
                limit :size
                offset :offset
                """, TABLE, PageHelper.orderBy(pageable.getSort()));

        var posts = jdbcTemplate.query(sql, params, rowMapper);


        return new PageImpl(posts, pageable, getCount(memberId));
    }

    private long getCount(Long memberId) {
        var sql = String.format("""
                select count(id)
                from %s
                where memberId = :memberId
                """, TABLE);

        var params = new MapSqlParameterSource().addValue("memberId", memberId);
        return jdbcTemplate.queryForObject(sql, params, Long.class);
    }

    public List<Post> findAllByMemberIdAndOrderByIdDesc(Long memberId, int size) {
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

    public List<Post> findAllByMemberIdAndOrderByIdDesc(List<Long> memberIds, int size) {
        if (memberIds.isEmpty()) {
            return List.of();
        }

        var sql = String.format("""
                select *
                from %s
                where memberId in (:memberIds)
                order by  id desc
                limit :size
                """, TABLE);

        var params = new MapSqlParameterSource()
                .addValue("memberIds", memberIds)
                .addValue("size", size);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public List<Post> findAllByInId(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }

        var sql = String.format("""
                select *
                from %s
                where id in (:ids)
                """, TABLE);

        var params = new MapSqlParameterSource()
                .addValue("ids", postIds);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public List<Post> findAllByLessThanMemberIdAndOrderByIdDesc(Long id, Long memberId, int size) {
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

    public List<Post> findAllByLessThanMemberIdAndOrderByIdDesc(Long id, List<Long> memberIds, int size) {
        if (memberIds.isEmpty()) {
            return List.of();
        }

        var sql = String.format("""
                select *
                from %s
                where memberId in (:memberIds) and id < :id
                order by  id desc
                limit :size
                """, TABLE);

        var params = new MapSqlParameterSource()
                .addValue("memberIds", memberIds)
                .addValue("id", id)
                .addValue("size", size);

        return jdbcTemplate.query(sql, params, rowMapper);
    }

    public Post save(Post post) {
        if (post.getId() == null) {
            return insert(post);
        }

        throw new UnsupportedOperationException("Post는 갱신을 지원하지 않습니다.");
    }

    public void bulkInsert(List<Post> posts) {
        var sql = String.format("insert into `%s` (memberId, contents, createdDate, createdAt) values (:memberId, :contents, :createdDate, :createdAt)", TABLE);

        SqlParameterSource[] params = posts.stream()
                .map(BeanPropertySqlParameterSource::new)
                .toArray(SqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(sql, params);
    }

    private Post insert(Post post) {
        SimpleJdbcInsert simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getJdbcTemplate())
                .withTableName(TABLE)
                .usingGeneratedKeyColumns("id");

        SqlParameterSource params = new BeanPropertySqlParameterSource(post);
        var memberId = simpleJdbcInsert.executeAndReturnKey(params).longValue();

        return Post.builder()
                .id(memberId)
                .memberId(post.getMemberId())
                .contents(post.getContents())
                .createdDate(post.getCreatedDate())
                .createdAt(post.getCreatedAt())
                .build();
    }
}
