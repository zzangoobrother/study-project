package com.example.domain.customer;

import com.example.exception.NotFoundCustomerException;
import com.example.repository.customer.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class CustomerDetailService implements UserDetailsService {

    private final CustomerRepository customerRepository;

    public CustomerDetailService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.info(">>> 회원 정보 찾기, {}", email);
        Customer customer = this.customerRepository.findByEmail(email);
        if (customer == null) {
            throw new NotFoundCustomerException("회원 정보를 찾을 수 없습니다." + email);
        }

        CustomerDetail customerDetail = new CustomerDetail();
        customerDetail.setCustomer(customer);

        return customerDetail;
    }
}
