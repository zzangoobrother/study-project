package com.fast.loan.service;

import com.fast.loan.domain.Application;
import com.fast.loan.domain.Entry;
import com.fast.loan.dto.BalanceDTO;
import com.fast.loan.dto.EntryDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.exception.ResultType;
import com.fast.loan.repository.ApplicationRepository;
import com.fast.loan.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntryServiceImpl implements EntryService {

    private final EntryRepository entryRepository;
    private final ApplicationRepository applicationRepository;
    private final BalanceService balanceService;
    private final ModelMapper modelMapper;

    @Override
    public EntryDTO.Response create(Long applicationId, EntryDTO.Request request) {
        if (!isContractedApplication(applicationId)) {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        }

        Entry entry = modelMapper.map(request, Entry.class);
        entry.setApplicationId(applicationId);

        entryRepository.save(entry);

        balanceService.create(applicationId,
                BalanceDTO.Request.builder()
                        .entryAmount(request.getEntryAmount())
                        .build());

       return modelMapper.map(entry, EntryDTO.Response.class);
    }

    @Override
    public EntryDTO.Response get(Long applicationId) {
        Optional<Entry> entry = entryRepository.findByApplicationId(applicationId);

        if (entry.isPresent()) {
            return modelMapper.map(entry, EntryDTO.Response.class);
        }

        return null;
    }

    @Override
    public EntryDTO.UpdateResponse update(Long entryId, EntryDTO.Request request) {
        Entry entry = entryRepository.findById(entryId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        BigDecimal beforeEntryAmount = request.getEntryAmount();
        entry.setEntryAmount(request.getEntryAmount());

        entryRepository.save(entry);

        Long applicationId = entry.getApplicationId();
        balanceService.update(applicationId,
                BalanceDTO.UpdateRequest.builder()
                        .beforeEntryAmount(beforeEntryAmount)
                        .afterEntryAmount(request.getEntryAmount())
                        .build());

        return EntryDTO.UpdateResponse.builder()
                .entryId(entryId)
                .applicationId(applicationId)
                .beforeEntryAmount(beforeEntryAmount)
                .afterEntryAmount(request.getEntryAmount())
                .build();
    }

    @Override
    public void delete(Long entryId) {
        Entry entry = entryRepository.findById(entryId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        entry.setIsDeleted(true);
        entryRepository.save(entry);

        Long applicationId = entry.getApplicationId();
        BigDecimal beforeEntryAmount = entry.getEntryAmount();
        balanceService.update(applicationId, BalanceDTO.UpdateRequest.builder()
                .beforeEntryAmount(beforeEntryAmount)
                .afterEntryAmount(BigDecimal.ZERO)
                .build());
    }

    private boolean isContractedApplication(Long applicationId) {
        Optional<Application> existed = applicationRepository.findById(applicationId);
        if (existed.isEmpty()) {
            return false;
        }

        return existed.get().getContractedAt() != null;
    }
}
