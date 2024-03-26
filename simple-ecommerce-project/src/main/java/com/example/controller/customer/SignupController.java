package com.example.controller.customer;

import com.example.controller.dto.customer.CustomerRegisterDTO;
import com.example.domain.customer.Customer;
import com.example.service.customer.CustomerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
public class SignupController {

    private final CustomerService customerService;
    private final PasswordEncoder passwordEncoder;

    public SignupController(CustomerService customerService, PasswordEncoder passwordEncoder) {
        this.customerService = customerService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/customer/signup")
    public String signupForm() {
        return "/customer/signup";
    }

    @PostMapping("/customer/signup")
    public String signup(CustomerRegisterDTO customerRegisterDTO, RedirectAttributes redirectAttributes) {
        log.info(">>> 회원 가입 정보: {}", customerRegisterDTO);

        customerService.validateDuplicatedCustomer(customerRegisterDTO.email());

        Customer generalCustomer = Customer.createGeneralCustomer(customerRegisterDTO, passwordEncoder);
        customerService.save(generalCustomer);

        redirectAttributes.addAttribute("customer", customerRegisterDTO.email());
        return "redirect:/customer/signup-success";
    }

    @GetMapping("/customer/signup-success")
    public String signupSuccess(RedirectAttributes redirectAttributes) {
        log.info(">>> 회원 가입 성공, {}", redirectAttributes.getAttribute("customer"));
        return "/customer/signup-success";
    }
}
