package com.fast.loan.service;

import com.fast.loan.dto.CounselDTO;

public interface CounselService {

    CounselDTO.Response create(CounselDTO.Request request);

    CounselDTO.Response get(Long counselId);

    CounselDTO.Response update(Long counselId, CounselDTO.Request request);

    void delete(Long counselId);
}
