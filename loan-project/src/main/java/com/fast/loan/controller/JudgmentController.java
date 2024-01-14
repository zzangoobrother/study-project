package com.fast.loan.controller;

import com.fast.loan.dto.JudgmentDTO;
import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.service.JudgmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/judgments")
@RequiredArgsConstructor
@RestController
public class JudgmentController {

    private final JudgmentService judgmentService;

    @PostMapping
    public ResponseDTO<JudgmentDTO.Response> create(@RequestBody JudgmentDTO.Request request) {
        return ResponseDTO.ok(judgmentService.create(request));
    }
}
