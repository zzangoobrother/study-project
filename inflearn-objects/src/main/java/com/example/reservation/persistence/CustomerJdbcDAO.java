package com.example.reservation.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.example.reservation.domain.Customer;
import com.example.reservation.service.CustomerDAO;

@Repository
public class CustomerJdbcDAO implements CustomerDAO {

    private JdbcClient jdbcClient;

    public CustomerJdbcDAO(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Customer find(Long id) {
        return jdbcClient.sql("SELECT ID, NAME FROM CUSTOMER WHERE ID = :id")
                .param("id", id)
                .query((rs, rowNum) -> new Customer(rs.getLong("id"), rs.getString("NAME")))
                .single();
    }
}
