package com.example.reservation.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import com.example.reservation.domain.Reservation;
import com.example.reservation.service.ReservationDAO;

@Repository
public class ReservationJdbcDAO implements ReservationDAO {
    private JdbcClient jdbcClient;

    public ReservationJdbcDAO(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void insert(Reservation reservation) {
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcClient.sql("INSERT INTO RESERVATION(CUSTOMER_ID, SCREENING_ID, AUDIENCE_COUNT, FEE)" +
                "VALUES(:customerId, :screeningId, :audienceCount, :fee)")
                .param("customerId", reservation.getCustomer().getId())
                .param("screeningId", reservation.getScreening().getId())
                .param("audienceCount", reservation.getAudienceCount())
                .param("fee", reservation.getFee().longValue())
                .update(keyHolder);

        reservation.setId(keyHolder.getKey().longValue());
    }
}
