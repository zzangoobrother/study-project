package com.example.hello;

import org.springframework.stereotype.Component;

// Activity 구현. Spring 빈으로 등록되어 워커에 연결된다(빈 이름: helloActivityImpl).
@Component
public class HelloActivityImpl implements HelloActivity {

    @Override
    public String composeGreeting(String name) {
        return "Hello, " + name + "!";
    }
}
