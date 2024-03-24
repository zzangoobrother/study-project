package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CustomerController {

    @GetMapping({"/customers", "/customers/"})
    public String index() {
        return "/customers/customers";
    }

    @GetMapping("/customers/customer-detail")
    public String detail() {
        return "/customers/customer-detail";
    }
}
