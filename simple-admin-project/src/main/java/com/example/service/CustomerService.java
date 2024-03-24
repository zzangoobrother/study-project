package com.example.service;

import com.example.domain.entity.Customer;
import com.example.exception.NotFoundCustomerException;
import com.example.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public List<Customer> findTop100ByActiveCustomer() {
        return customerRepository.findTop100ByIsDeletedIsFalse();
    }

    public Customer findById(Long customerId) {
        return customerRepository.findById(customerId).orElseThrow(
                () -> new NotFoundCustomerException("고객 정보를 찾을 수 없습니다." + customerId)
        );
    }
}
