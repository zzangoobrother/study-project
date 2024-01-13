package com.fast.loan.service;

import com.fast.loan.dto.ApplicationDTO;

public interface ApplicationService {

    ApplicationDTO.Response create(ApplicationDTO.Request request);

    ApplicationDTO.Response get(Long applicationId);

    ApplicationDTO.Response update(Long applicationId, ApplicationDTO.Request request);

    void delete(Long applicationId);
}
