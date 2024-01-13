package com.fast.loan.service;

import com.fast.loan.dto.TermsDTO;

import java.util.List;

public interface TermsService {
    TermsDTO.Response create(TermsDTO.Request request);

    List<TermsDTO.Response> getAll();
}
