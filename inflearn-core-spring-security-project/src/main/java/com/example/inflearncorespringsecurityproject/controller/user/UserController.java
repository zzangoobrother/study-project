package com.example.inflearncorespringsecurityproject.controller.user;

import com.example.inflearncorespringsecurityproject.domain.entity.Account;
import com.example.inflearncorespringsecurityproject.domain.dto.AccountDto;
import com.example.inflearncorespringsecurityproject.service.AccountService;
import com.example.inflearncorespringsecurityproject.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class UserController {

    private final AccountService accountService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public UserController(AccountService accountService, UserService userService, PasswordEncoder passwordEncoder) {
        this.accountService = accountService;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/mypage")
    public String myPage() {
        userService.order();
        return "user/mypage";
    }

    @GetMapping("/users")
    public String createUser() {
        return "user/login/register";
    }

    @PostMapping("/users")
    public String createUser(AccountDto accountDto) {
        ModelMapper modelMapper = new ModelMapper();
        Account account = modelMapper.map(accountDto, Account.class);
        account.setPassword(passwordEncoder.encode(accountDto.getPassword()));

        accountService.createUser(account);

        return "redirect:/";
    }
}
