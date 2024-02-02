package com.example.fastcampusmysql.controller;

import com.example.fastcampusmysql.domain.member.dto.RegisterMemberCommand;
import com.example.fastcampusmysql.domain.member.service.MemberWriteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

    private final MemberWriteService memberWriteService;

    public MemberController(MemberWriteService memberWriteService) {
        this.memberWriteService = memberWriteService;
    }

    @PostMapping("/members")
    public void register(@RequestBody RegisterMemberCommand command) {
        memberWriteService.create(command);
    }
}
