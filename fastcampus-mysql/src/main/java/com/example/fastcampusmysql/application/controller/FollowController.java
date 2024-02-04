package com.example.fastcampusmysql.application.controller;

import com.example.fastcampusmysql.application.usecase.CreateFollowMemberUsecase;
import com.example.fastcampusmysql.application.usecase.GetFollowingMemberUsecase;
import com.example.fastcampusmysql.domain.member.dto.MemberDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/follow")
@RestController
public class FollowController {

    private final CreateFollowMemberUsecase createFollowMemberUsecase;
    private final GetFollowingMemberUsecase getFollowingMemberUsecase;

    public FollowController(CreateFollowMemberUsecase createFollowMemberUsecase, GetFollowingMemberUsecase getFollowingMemberUsecase) {
        this.createFollowMemberUsecase = createFollowMemberUsecase;
        this.getFollowingMemberUsecase = getFollowingMemberUsecase;
    }

    @PostMapping("/{fromId}/{toId}")
    public void create(@PathVariable Long fromId, @PathVariable Long toId) {
        createFollowMemberUsecase.execute(fromId, toId);
    }

    @GetMapping("/members/{fromId}}")
    public List<MemberDto> create(@PathVariable Long fromId) {
        return getFollowingMemberUsecase.execute(fromId);
    }
}
