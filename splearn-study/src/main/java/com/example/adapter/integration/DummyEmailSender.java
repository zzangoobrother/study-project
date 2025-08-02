package com.example.adapter.integration;

import com.example.application.required.EmailSender;
import com.example.domain.Email;
import org.springframework.stereotype.Component;

@Component
public class DummyEmailSender implements EmailSender {
    @Override
    public void send(Email email, String subjext, String body) {
        System.out.println("DummyEmailSender send email : " + email);
    }
}
