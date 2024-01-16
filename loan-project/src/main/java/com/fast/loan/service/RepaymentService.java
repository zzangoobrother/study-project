package com.fast.loan.service;

import com.fast.loan.dto.RepaymentDTO;

import java.util.List;

public interface RepaymentService {

    RepaymentDTO.Response create(Long applicationId, RepaymentDTO.Request request);

    List<RepaymentDTO.ListResponse> get(Long applicationId);
}
