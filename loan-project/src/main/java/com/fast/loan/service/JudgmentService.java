package com.fast.loan.service;

import com.fast.loan.dto.JudgmentDTO;

public interface JudgmentService {

    JudgmentDTO.Response create(JudgmentDTO.Request request);

    JudgmentDTO.Response get(Long judgmentId);

    JudgmentDTO.Response getJudgmentOfApplication(Long applicationId);
}
