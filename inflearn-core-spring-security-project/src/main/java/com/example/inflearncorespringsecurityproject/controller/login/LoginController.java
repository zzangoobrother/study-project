package com.example.inflearncorespringsecurityproject.controller.login;

import com.example.inflearncorespringsecurityproject.domain.Account;
import com.example.inflearncorespringsecurityproject.security.token.AjaxAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;

@Slf4j
@Controller
public class LoginController {

    @GetMapping(value = {"/login", "/api/login"})
    public String login(@RequestParam(required = false) String error, @RequestParam(required = false) String exception, Model model) {
        log.info("로그인");

        model.addAttribute("error", error);
        model.addAttribute("exception", exception);
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("로그아웃");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null) {
            log.info("authentication 삭제");
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        return "redirect:/login";
    }

    @GetMapping(value = {"/denied", "/api/denied"})
    public String accessDenied(@RequestParam(required = false) String exception, Principal principal, Model model) {
        Account account = null;
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            account = (Account) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        } else if (principal instanceof AjaxAuthenticationToken) {
            account = (Account) ((AjaxAuthenticationToken) principal).getPrincipal();
        }

        model.addAttribute("username", account.getUsername());
        model.addAttribute("exception", exception);

        return "user/login/denied";
    }
}
