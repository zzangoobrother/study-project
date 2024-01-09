package com.project.adminboard.controller.advice;

import com.project.adminboard.service.VisitCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@RequiredArgsConstructor
@ControllerAdvice
public class VisitCounterControllerAdvice {

    private final VisitCountService visitCountService;

    @ModelAttribute("visitCount")
    public Long visitCount() {
        return visitCountService.visitCount();
    }
}
