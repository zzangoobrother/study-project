package com.example.controller.customer;

import com.example.controller.dto.customer.CustomerDTO;
import com.example.controller.dto.customer.CustomerUpdateDTO;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class CustomerController {

    @GetMapping(value = "/customer/{customerId}")
    public CustomerDTO get(@PathVariable String customerId) {
        return null;
    }

    @PostMapping(value = "/customer/update")
    public CustomerDTO update(@RequestBody CustomerUpdateDTO customerUpdateDTO) {
        return null;
    }
}
