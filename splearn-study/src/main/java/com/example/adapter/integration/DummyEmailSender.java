package com.example.adapter.integration;

import com.example.application.member.required.EmailSender;
import com.example.domain.shared.Email;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Component;

@Fallback
@Component
public class DummyEmailSender implements EmailSender {
    @Override
    public void send(Email email, String subjext, String body) {
        System.out.println("DummyEmailSender send email : " + email);
    }
}
