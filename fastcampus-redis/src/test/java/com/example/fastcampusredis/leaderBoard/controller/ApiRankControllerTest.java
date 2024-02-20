package com.example.fastcampusredis.leaderBoard.controller;

import com.example.fastcampusredis.leaderBoard.service.RankingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SpringBootTest
class ApiRankControllerTest {

    @Autowired
    private RankingService rankingService;

    @Test
    void getRanks() {
        Instant before = Instant.now();
        Long userRank = rankingService.getUserRanking("user_100");
        Duration elapsed = Duration.between(before, Instant.now());

        System.out.println((elapsed.getNano() / 1000000) + " ms");
        System.out.println(String.format("Rank(%d) - Took %d ms", userRank, (elapsed.getNano() / 1000000)));

        before = Instant.now();
        List<String> topRank = rankingService.getTopRank(10);
        elapsed = Duration.between(before, Instant.now());

        System.out.println(String.format("Rank - Took %d ms", (elapsed.getNano() / 1000000)));
    }

    @Test
    void insertScore() {
        for (int i = 0; i < 1000000; i++) {
            int score = (int) (Math.random() * 1000000);
            String userId = "user_" + i;
            rankingService.setUserScore(userId, score);
        }
    }

    @Test
    void inMemorySortPerformance() {
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            int score = (int) (Math.random() * 1000000);
            list.add(score);
        }

        Instant before = Instant.now();
        Collections.sort(list);
        Duration elapsed = Duration.between(before, Instant.now());
        System.out.println((elapsed.getNano() / 1000000) + " ms");
    }
}
