package com.project.adminboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping
@Controller
public class MainController {

    @GetMapping("/")
    public String root() {
        return "forward:/management/articles";
    }
}
