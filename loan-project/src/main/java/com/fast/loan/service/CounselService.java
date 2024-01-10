package com.fast.loan.service;

import com.fast.loan.dto.CounselDTO;

public interface CounselService {

    CounselDTO.Response create(CounselDTO.Request request);
}
