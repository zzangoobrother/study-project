package com.fast.loan.controller;

import com.fast.loan.dto.JudgmentDTO;
import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.service.JudgmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/judgments")
@RequiredArgsConstructor
@RestController
public class JudgmentController {

    private final JudgmentService judgmentService;

    @PostMapping
    public ResponseDTO<JudgmentDTO.Response> create(@RequestBody JudgmentDTO.Request request) {
        return ResponseDTO.ok(judgmentService.create(request));
    }

    @GetMapping("/{judgmentId}")
    public ResponseDTO<JudgmentDTO.Response> get(@PathVariable Long judgmentId) {
        return ResponseDTO.ok(judgmentService.get(judgmentId));
    }

    @GetMapping("/applications/{applicationId}")
    public ResponseDTO<JudgmentDTO.Response> getJudgmentOfApplication(@PathVariable Long applicationId) {
        return ResponseDTO.ok(judgmentService.getJudgmentOfApplication(applicationId));
    }
}
