package com.fast.loan.service;

import com.fast.loan.domain.Application;
import com.fast.loan.domain.Entry;
import com.fast.loan.domain.Repayment;
import com.fast.loan.dto.BalanceDTO;
import com.fast.loan.dto.RepaymentDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.exception.ResultType;
import com.fast.loan.repository.ApplicationRepository;
import com.fast.loan.repository.EntryRepository;
import com.fast.loan.repository.RepaymentRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepaymentServiceImpl implements RepaymentService {

    private final RepaymentRepository repaymentRepository;
    private final ApplicationRepository applicationRepository;
    private final EntryRepository entryRepository;
    private final BalanceService balanceService;
    private final ModelMapper modelMapper;

    @Override
    public RepaymentDTO.Response create(Long applicationId, RepaymentDTO.Request request) {
        if (!isRepayableApplication(applicationId)) {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        }

        Repayment repayment = modelMapper.map(request, Repayment.class);
        repayment.setApplicationId(applicationId);

        repaymentRepository.save(repayment);

        BalanceDTO.Response updateBalance = balanceService.repaymentUpdate(applicationId,
                BalanceDTO.RepaymentRequest.builder()
                        .repaymentAmount(request.getRepaymentAmount())
                        .type(BalanceDTO.RepaymentRequest.RepaymentType.REMOVE)
                        .build());

        RepaymentDTO.Response response = modelMapper.map(repayment, RepaymentDTO.Response.class);
        response.setBalance(updateBalance.getBalance());

        return response;
    }

    @Override
    public List<RepaymentDTO.ListResponse> get(Long applicationId) {
        List<Repayment> repayments = repaymentRepository.findAllByApplicationId(applicationId);
        return repayments.stream()
                .map(repayment -> modelMapper.map(repayment, RepaymentDTO.ListResponse.class))
                .toList();
    }

    @Override
    public RepaymentDTO.UpdateResponse update(Long repaymentId, RepaymentDTO.Request request) {
        Repayment repayment = repaymentRepository.findById(repaymentId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        Long applicationId = repayment.getApplicationId();
        BigDecimal beforeRepaymentAmount = repayment.getRepaymentAmount();

        balanceService.repaymentUpdate(applicationId, BalanceDTO.RepaymentRequest.builder()
                .repaymentAmount(beforeRepaymentAmount)
                .type(BalanceDTO.RepaymentRequest.RepaymentType.ADD)
                .build());

        repayment.setRepaymentAmount(request.getRepaymentAmount());
        repaymentRepository.save(repayment);

        BalanceDTO.Response response = balanceService.repaymentUpdate(applicationId, BalanceDTO.RepaymentRequest.builder()
                .repaymentAmount(request.getRepaymentAmount())
                .type(BalanceDTO.RepaymentRequest.RepaymentType.REMOVE)
                .build());

        return RepaymentDTO.UpdateResponse.builder()
                .applicationId(applicationId)
                .beforeRepaymentAmount(beforeRepaymentAmount)
                .afterRepaymentAmount(repayment.getRepaymentAmount())
                .balance(response.getBalance())
                .createdAt(repayment.getCreatedAt())
                .updatedAt(repayment.getUpdatedAt())
                .build();
    }

    @Override
    public void delete(Long repaymentId) {
        Repayment repayment = repaymentRepository.findById(repaymentId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        Long applicationId = repayment.getApplicationId();
        BigDecimal removeRepaymentAmount = repayment.getRepaymentAmount();

        balanceService.repaymentUpdate(applicationId, BalanceDTO.RepaymentRequest.builder()
                .repaymentAmount(removeRepaymentAmount)
                .type(BalanceDTO.RepaymentRequest.RepaymentType.ADD)
                .build());

        repayment.setIsDeleted(true);
        repaymentRepository.save(repayment);
    }

    private boolean isRepayableApplication(Long applicationId) {
        Optional<Application> existedApplication = applicationRepository.findById(applicationId);
        if (existedApplication.isEmpty()) {
            return false;
        }

        if (existedApplication.get().getContractedAt() == null) {
            return false;
        }

        Optional<Entry> existedEntry = entryRepository.findByApplicationId(applicationId);
        return existedEntry.isPresent();
    }
}
