package com.fast.loan.controller;

import com.fast.loan.dto.CounselDTO;
import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.service.CounselService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping("/counsels")
@RestController
public class CounselController extends AbstractController {

    private final CounselService counselService;

    @PostMapping
    public ResponseDTO<CounselDTO.Response> create(@RequestBody CounselDTO.Request request) {
        return ok(counselService.create(request));
    }

    @GetMapping("/{counselId}")
    public ResponseDTO<CounselDTO.Response> get(@PathVariable Long counselId) {
        return ok(counselService.get(counselId));
    }
}
