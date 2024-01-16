package com.fast.loan.controller;

import com.fast.loan.dto.EntryDTO;
import com.fast.loan.dto.RepaymentDTO;
import com.fast.loan.dto.ResponseDTO;
import com.fast.loan.service.EntryService;
import com.fast.loan.service.RepaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/internal/applications")
public class InternalController extends AbstractController{

    private final EntryService entryService;
    private final RepaymentService repaymentService;

    @PostMapping("/{applicationId}/entries")
    public ResponseDTO<EntryDTO.Response> create(@PathVariable Long applicationId, @RequestBody EntryDTO.Request request) {
        return ok(entryService.create(applicationId, request));
    }

    @GetMapping("/{applicationId}/entries")
    public ResponseDTO<EntryDTO.Response> get(@PathVariable Long applicationId) {
        return ok(entryService.get(applicationId));
    }

    @PutMapping("/entries/{entryId}")
    public ResponseDTO<EntryDTO.UpdateResponse> update(@PathVariable Long entryId, @RequestBody EntryDTO.Request request) {
        return ok(entryService.update(entryId, request));
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseDTO<Void> delete(@PathVariable Long entryId) {
        entryService.delete(entryId);
        return ok();
    }

    @PostMapping("/{applicationId}/repayments")
    public ResponseDTO<RepaymentDTO.Response> create(@PathVariable Long applicationId, @RequestBody RepaymentDTO.Request request) {
        return ok(repaymentService.create(applicationId, request));
    }
}
