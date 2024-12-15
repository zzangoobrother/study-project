package com.example.processor.resolver;

import com.example.http.HttpRequest;
import com.example.web.user.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

class LoginArgumentResolverTest {

    private LoginArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LoginArgumentResolver();
    }

    @Test
    void 정상적인_요청_파라미터로_LoginRequest_객채_생성() {
        String body = "userId=test@naver.com&password=1234";
        HttpRequest request = HttpRequest.builder()
                .method("POST")
                .path("/login")
                .version("HTTP/1.1")
                .headers(new HashMap<>())
                .body(body)
                .build();

        LoginRequest loginRequest = resolver.resolve(request);

        assertThat(loginRequest)
                .isNotNull()
                .extracting(LoginRequest::getUserId, LoginRequest::getPassword)
                .containsExactly("test@naver.com", "1234");
    }

    @Test
    void 이메일_누락_시_예외_발생() {
        String body = "password=1234";
        HttpRequest request = HttpRequest.builder()
                .method("POST")
                .path("/login")
                .version("HTTP/1.1")
                .headers(new HashMap<>())
                .body(body)
                .build();

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("필수 파라미터가 누락되어있습니다.");
    }

    @Test
    void 비밀번호_누락_시_예외_발생() {
        String body = "userId=test@naver.com";
        HttpRequest request = HttpRequest.builder()
                .method("POST")
                .path("/login")
                .version("HTTP/1.1")
                .headers(new HashMap<>())
                .body(body)
                .build();

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("필수 파라미터가 누락되어있습니다.");
    }

    @Test
    void 이메일과_비밀번호_누락_시_예외_발생() {
        String body = "";
        HttpRequest request = HttpRequest.builder()
                .method("POST")
                .path("/login")
                .version("HTTP/1.1")
                .headers(new HashMap<>())
                .body(body)
                .build();

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("필수 파라미터가 누락되어있습니다.");
    }
}
