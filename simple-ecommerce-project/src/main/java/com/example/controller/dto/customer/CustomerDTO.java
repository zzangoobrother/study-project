package com.example.controller.dto.customer;

import com.example.domain.customer.Customer;

public record CustomerDTO(
        Long customerId,
        String customerName,
        String email,
        String address,
        String phoneNumber
) {

    public static CustomerDTO of(Customer customer) {
        return new CustomerDTO(
                customer.getCustomerId(),
                customer.getName(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getPhoneNumber());
    }
}
