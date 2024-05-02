package com.example.inflearncorespringsecurityproject.aopSecurity;

import com.example.inflearncorespringsecurityproject.domain.dto.AccountDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AopSecurityController {

    @GetMapping("/preAuthorize")
    @PreAuthorize("hasRole('ROLE_USER') and #account.username == principal.username")
    public String preAuthorize(AccountDto account, Model model) {
        model.addAttribute("method", "Success @PreAuthorize");

        return "aop/method";
    }
}
