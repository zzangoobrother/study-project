package com.example.controller;

import com.example.dto.AllowUserResponse;
import com.example.dto.AllowedUserResponse;
import com.example.dto.RankNumberResponse;
import com.example.dto.RegisterUserResponse;
import com.example.service.UserQueueService;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RequestMapping("/api/v1/queue")
@RestController
public class UserQueueController {

    private final UserQueueService userQueueService;

    public UserQueueController(UserQueueService userQueueService) {
        this.userQueueService = userQueueService;
    }

    @PostMapping
    public Mono<RegisterUserResponse> registerUser(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam("userId") Long userId) {
        return userQueueService.registerWaitQueue(queue, userId)
                .map(RegisterUserResponse::new);
    }

    @PostMapping("/allow")
    public Mono<AllowUserResponse> allowUser(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam("count") Long count) {
        return userQueueService.allowUser(queue, count)
                .map(allowed -> new AllowUserResponse(count, allowed));
    }

    @GetMapping("/allowed")
    public Mono<AllowedUserResponse> isAllowedUSer(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam("userId") Long userId, @RequestParam("token") String token) {
        return userQueueService.isAllowedByToken(queue, userId, token)
                .map(AllowedUserResponse::new);
    }

    @GetMapping("/rank")
    public Mono<RankNumberResponse> getRankUser(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam("userId") Long userId) {
        return userQueueService.getRank(queue, userId)
                .map(RankNumberResponse::new);
    }

    @GetMapping("/touch")
    public Mono<?> touch(@RequestParam(name = "queue", defaultValue = "default") String queue, @RequestParam("userId") Long userId, ServerWebExchange exchange) {
        return Mono.defer(() -> userQueueService.generateToken(queue, userId))
                .map(token -> {
                    exchange.getResponse().addCookie(
                            ResponseCookie.from("user-queue-%s-token".formatted(queue), token)
                                    .maxAge(Duration.ofSeconds(300))
                                    .path("/")
                                    .build()
                    );

                    return token;
                });
    }
}
