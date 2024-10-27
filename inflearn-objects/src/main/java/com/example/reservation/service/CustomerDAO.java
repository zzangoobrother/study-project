package com.example.reservation.service;

import com.example.reservation.domain.Customer;

public interface CustomerDAO {

    Customer find(Long id);
}
