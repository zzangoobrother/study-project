package com.example.service.customer;

import com.example.domain.customer.Customer;
import com.example.exception.DuplicatedEmailException;
import com.example.repository.customer.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void validateDuplicatedCustomer(String email) {
        Optional<Customer> optionalCustomer = Optional.ofNullable(customerRepository.findByEmail(email));
        if (optionalCustomer.isPresent()) {
            throw new DuplicatedEmailException("중복 이메일입니다.");
        }
    }

    public Customer save(Customer customer) {
        return customerRepository.save(customer);
    }
}
