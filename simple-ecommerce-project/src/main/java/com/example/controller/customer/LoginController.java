package com.example.controller.customer;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping(value = "/customer/login")
    public String loginForm(@RequestParam(value = "error", defaultValue = "false") boolean error, Model model) {
        if (error) {
            model.addAttribute("loginFailure", true);
            model.addAttribute("loginFailureMessage", "아이디와 패스워드를 확인하시고 다시 로그인해주세요.");
        }
        return "customer/login";
    }
}
