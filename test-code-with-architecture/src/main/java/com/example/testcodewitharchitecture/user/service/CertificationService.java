package com.example.testcodewitharchitecture.user.service;

import com.example.testcodewitharchitecture.user.service.port.MailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class CertificationService {

    private final MailSender mailSender;

    public void send(String email, long userId, String certificationCode) {
        String title = "Please certify your email address";
        String content = "Please click the following link to certify your email address : " + generateCertificationUrl(userId, certificationCode);

        mailSender.send(email, title, content);
    }

    private String generateCertificationUrl(long userId, String certificationCode) {
        return "http://localhost:8080/api/users/" + userId + "/verify?certificationCode=" + certificationCode;
    }
}
