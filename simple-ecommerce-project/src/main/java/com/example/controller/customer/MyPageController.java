package com.example.controller.customer;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyPageController {

    @GetMapping(value = {"/customer/mypage", "/customer/mypage/"})
    public String myPage() {
        // todo 구매 내역
        return "my-page";
    }
}
