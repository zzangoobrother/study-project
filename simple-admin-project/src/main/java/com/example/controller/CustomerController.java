package com.example.controller;

import com.example.domain.entity.Customer;
import com.example.service.CustomerService;
import com.example.service.dto.CustomerDTO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class CustomerController {
    private static final String CUSTOMERS_ATTRIBUTE_KEY = "customers";

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping({"/customers", "/customers/"})
    public String index(Model model) {
        List<Customer> customers = customerService.findTop100ByActiveCustomer();
        List<CustomerDTO> customerDTOS = customers.stream()
                .map(customer -> CustomerDTO.builder()
                        .customerId(customer.getCustomerId())
                        .customerName(customer.getCustomerName())
                        .phoneNumber(customer.getPhoneNumber())
                        .address(customer.getAddress())
                        .customerGrade(customer.getGrade())
                        .createdAt(customer.getCreatedAt())
                        .updatedAt(customer.getUpdatedAt())
                        .build())
                .toList();

        model.addAttribute(CUSTOMERS_ATTRIBUTE_KEY, customerDTOS);
        return "/customers/customers";
    }

    @GetMapping("/customers/customer-detail")
    public String detail(@RequestParam Long customerId, Model model) {
        Customer customer = customerService.findById(customerId);
        model.addAttribute("customer", customer);
        return "/customers/customer-detail";
    }
}
