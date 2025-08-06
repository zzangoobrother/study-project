package com.example.application.member.required;

import com.example.domain.shared.Email;

public interface EmailSender {
    void send(Email email, String subjext, String body);
}
