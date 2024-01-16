package com.fast.loan.service;

import com.fast.loan.dto.RepaymentDTO;

public interface RepaymentService {

    RepaymentDTO.Response create(Long applicationId, RepaymentDTO.Request request);
}
