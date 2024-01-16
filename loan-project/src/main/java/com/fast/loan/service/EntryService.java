package com.fast.loan.service;

import com.fast.loan.dto.EntryDTO;

public interface EntryService {
    EntryDTO.Response create(Long applicationId, EntryDTO.Request request);

    EntryDTO.Response get(Long applicationId);

    EntryDTO.UpdateResponse update(Long entryId, EntryDTO.Request request);

    void delete(Long entryId);
}
