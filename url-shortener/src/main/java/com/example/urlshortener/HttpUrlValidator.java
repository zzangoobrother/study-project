package com.example.urlshortener;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 은 @NotBlank 가 처리한다
        if (value == null) {
            return true;
        }
        // 짝이 맞지 않는 UTF-16 서로게이트는 DB 에 '?' 로 저장돼 equals 비교가 늘 실패한다
        if (!new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).equals(value)) {
            return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return httpScheme && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
