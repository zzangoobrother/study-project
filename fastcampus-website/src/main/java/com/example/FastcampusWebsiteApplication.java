package com.example;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;

@Controller
@SpringBootApplication
public class FastcampusWebsiteApplication {

    RestTemplate restTemplate = new RestTemplate();

    public static void main(String[] args) {
        SpringApplication.run(FastcampusWebsiteApplication.class, args);
    }

    @GetMapping("/")
    public String index(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam(name = "userId") Long userId, HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String cookieName = "user-queue-%s-token".formatted(queue);

        String token = "";
        if (cookies != null) {
            token = Arrays.stream(cookies)
                    .filter(i -> i.getName().equalsIgnoreCase(cookieName))
                    .findFirst()
                    .orElse(new Cookie(cookieName, ""))
                    .getValue();
        }

        URI uri = UriComponentsBuilder
                .fromUriString("http://localhost:9010")
                .path("/api/v1/queue/allowed")
                .queryParam("queue", queue)
                .queryParam("userId", userId)
                .queryParam("token", token)
                .encode()
                .build()
                .toUri();

        ResponseEntity<AllowedUserResponse> response = restTemplate.getForEntity(uri, AllowedUserResponse.class);
        if (response.getBody() == null || !response.getBody().allowed) {
            return "redirect:http://localhost:9010/waiting-room?userId=%d&redirectUrl=%s".formatted(userId, "http://localhost:9000?userId=%d".formatted(userId));
        }

        return "index";
    }

    public record AllowedUserResponse(Boolean allowed) {

    }
}
