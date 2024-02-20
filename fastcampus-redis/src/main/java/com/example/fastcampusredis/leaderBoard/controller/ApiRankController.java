package com.example.fastcampusredis.leaderBoard.controller;

import com.example.fastcampusredis.leaderBoard.service.RankingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ApiRankController {

    private RankingService rankingService;

    public ApiRankController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping("/setScore")
    public boolean setScore(@RequestParam String userId, @RequestParam int score) {
        return rankingService.setUserScore(userId, score);
    }

    @GetMapping("/getRank")
    public long getUserRank(@RequestParam String userId) {
        return rankingService.getUserRanking(userId);
    }

    @GetMapping("/getTopRanks")
    public List<String> getTopRanks() {
        return rankingService.getTopRank(3);
    }
}
