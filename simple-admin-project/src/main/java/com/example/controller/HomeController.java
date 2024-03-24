package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("dailyPaymentCnt", 1_000_000);
        model.addAttribute("dailyCancelCnt", 50);
        model.addAttribute("dailyCustomerJoinCnt", 200);
        model.addAttribute("dailyCustomerWithdrawalCnt", 30);
        model.addAttribute("dailyProductRegCnt", 5_000);

        return "index";
    }
}
