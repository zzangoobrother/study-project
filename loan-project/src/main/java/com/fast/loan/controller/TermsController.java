package com.fast.loan.controller;

import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.dto.TermsDTO;
import com.fast.loan.service.TermsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.fast.loan.dto.ResponseDTO.ok;

@RequiredArgsConstructor
@RestController
@RequestMapping("/terms")
public class TermsController {

    private final TermsService termsService;

    @PostMapping
    public ResponseDTO<TermsDTO.Response> create(@RequestBody TermsDTO.Request request) {
        return ok(termsService.create(request));
    }

    @GetMapping
    public ResponseDTO<List<TermsDTO.Response>> getAll() {
        return ok(termsService.getAll());
    }
}
