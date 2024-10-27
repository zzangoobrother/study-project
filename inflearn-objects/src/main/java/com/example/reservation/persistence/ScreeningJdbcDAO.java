package com.example.reservation.persistence;

import com.example.generic.Money;
import com.example.reservation.domain.*;
import com.example.reservation.service.ScreeningDAO;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public class ScreeningJdbcDAO implements ScreeningDAO {
    private JdbcClient jdbcClient;

    public ScreeningJdbcDAO(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Screening find(Long screeningId) {
        return queryScreening(screeningId);
    }

    private Screening queryScreening(Long screeningId) {
        return jdbcClient.sql(
                        "SELECT ID, MOVIE_ID, SEQUENCE, SCREENING_TIME " +
                                "FROM SCREENING " +
                                "WHERE ID = :id")
                .param("id", screeningId)
                .query((rs, rowNum) -> new Screening(
                        rs.getLong("ID"),
                        queryMovie(rs.getLong("MOVIE_ID")),
                        rs.getInt("SEQUENCE"),
                        rs.getTimestamp("SCREENING_TIME").toLocalDateTime()))
                .single();
    }

    private Movie queryMovie(Long movieId) {
        return jdbcClient.sql(
                        "SELECT ID, TITLE, FEE " +
                                "FROM MOVIE " +
                                "WHERE ID = :id")
                .param("id", movieId)
                .query((rs, rowNum) -> new Movie(
                        rs.getLong("ID"),
                        rs.getString("TITLE"),
                        Money.wons(rs.getInt("FEE")),
                        queryDiscountPolicy(movieId)))
                .single();
    }

    private DiscountPolicy queryDiscountPolicy(Long movieId) {
        List<DiscountPolicy> policies = jdbcClient.sql(
                        "SELECT ID, POLICY_TYPE, AMOUNT, PERCENT " +
                                "FROM DISCOUNT_POLICY " +
                                "WHERE MOVIE_ID = :movieId")
                .param("movieId", movieId)
                .query((rs, rowNum) -> {
                    if (rs.getString("POLICY_TYPE").equals("AMOUNT")) {
                        return new AmountDiscountPolicy(
                                rs.getLong("ID"),
                                Money.wons(rs.getInt("AMOUNT")),
                                queryDiscountConditions(rs.getLong("ID")));
                    }

                    return new PercentDiscountPolicy(
                            rs.getLong("ID"),
                            rs.getDouble("PERCENT"),
                            queryDiscountConditions(rs.getLong("ID")));
                })
                .list();

        if (policies.size() > 1) {
            return new OverlappedDiscountPolicy(policies.toArray(new DiscountPolicy[0]));
        }

        return policies.isEmpty() ? null : policies.get(0);
    }

    private DiscountCondition[] queryDiscountConditions(Long policyId) {
        return jdbcClient.sql(
                        "SELECT ID, CONDITION_TYPE, SEQUENCE, DAY_OF_WEEK, START_TIME, END_TIME " +
                                "FROM DISCOUNT_CONDITION " +
                                "WHERE POLICY_ID = :policyId")
                .param("policyId", policyId)
                .query((rs, rowNum) -> {
                    if (rs.getString("CONDITION_TYPE").equals("SEQUENCE")) {
                        return new SequenceCondition(
                                rs.getLong("ID"),
                                rs.getInt("SEQUENCE"));
                    }

                    return new PeriodCondition(
                            rs.getLong("ID"),
                            DayOfWeek.valueOf(rs.getString("DAY_OF_WEEK")),
                            rs.getTime("START_TIME").toLocalTime(),
                            rs.getTime("END_TIME").toLocalTime());
                })
                .list().toArray(new DiscountCondition[0]);
    }

    @Override
    public void insert(Screening screening) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcClient.sql("INSERT INTO SCREENING(MOVIE_ID, SEQUENCE, WHEN_SCREENED)" +
                        "VALUES(:movieId, :sequence, :whenScreened)")
                .param("movieId", screening.getMovie().getId())
                .param("sequence", screening.getSequence())
                .param("whenScreened", screening.getWhenScreened())
                .update(keyHolder);

        screening.setId(keyHolder.getKey().longValue());
    }
}
