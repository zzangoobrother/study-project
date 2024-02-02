package com.example.fastcampusmysql.controller;

import com.example.fastcampusmysql.domain.member.dto.MemberDto;
import com.example.fastcampusmysql.domain.member.dto.RegisterMemberCommand;
import com.example.fastcampusmysql.domain.member.service.MemberReadService;
import com.example.fastcampusmysql.domain.member.service.MemberWriteService;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberController {

    private final MemberWriteService memberWriteService;
    private final MemberReadService memberReadService;

    public MemberController(MemberWriteService memberWriteService, MemberReadService memberReadService) {
        this.memberWriteService = memberWriteService;
        this.memberReadService = memberReadService;
    }

    @PostMapping("/members")
    public MemberDto register(@RequestBody RegisterMemberCommand command) {
        var member = memberWriteService.register(command);
        return memberReadService.toDto(member);
    }

    @GetMapping("/members/{id}")
    public MemberDto getMember(@PathVariable Long id) {
        return memberReadService.getMember(id);
    }
}
