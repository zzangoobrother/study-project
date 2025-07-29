package com.example.application.required;

import com.example.domain.Email;

public interface EmailSender {
    void send(Email email, String subjext, String body);
}
