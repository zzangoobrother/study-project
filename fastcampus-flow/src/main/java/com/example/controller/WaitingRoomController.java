package com.example.controller;

import com.example.service.UserQueueService;
import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
public class WaitingRoomController {

    private final UserQueueService userQueueService;

    public WaitingRoomController(UserQueueService userQueueService) {
        this.userQueueService = userQueueService;
    }

    @GetMapping("/waiting-room")
    public Mono<Rendering> waitingRoomPage(@RequestParam(value = "queue", defaultValue = "default") String queue, @RequestParam("userId") Long userId, @RequestParam("redirectUrl") String redirectUrl, ServerWebExchange exchange) {
        String key = "user-queue-%s-token".formatted(queue);
        HttpCookie cookieValue = exchange.getRequest().getCookies().getFirst(key);
        String token = cookieValue == null ? "" : cookieValue.getValue();

        return userQueueService.isAllowedByToken(queue, userId, token)
                .filter(allowed -> allowed)
                .flatMap(allowed -> Mono.just(Rendering.redirectTo(redirectUrl).build()))
                .switchIfEmpty(
                        userQueueService.registerWaitQueue(queue, userId)
                                .onErrorResume(ex -> userQueueService.getRank(queue, userId))
                                .map(rank -> Rendering.view("waiting-room.html")
                                        .modelAttribute("number", rank)
                                        .modelAttribute("userId", userId)
                                        .modelAttribute("queue", queue)
                                        .build())
                );
    }
}
