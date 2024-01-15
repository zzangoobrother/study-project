package com.fast.loan.service;

import com.fast.loan.domain.Balance;
import com.fast.loan.dto.BalanceDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.exception.ResultType;
import com.fast.loan.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {

    private final BalanceRepository balanceRepository;
    private final ModelMapper modelMapper;

    @Override
    public BalanceDTO.Response create(Long applicationId, BalanceDTO.Request request) {
        if (balanceRepository.findByApplicationId(applicationId).isPresent()) {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        }

        Balance balance = modelMapper.map(request, Balance.class);

        BigDecimal entryAmount = request.getEntryAmount();
        balance.setApplicationId(applicationId);
        balance.setBalance(entryAmount);

        Balance saveBalance = balanceRepository.save(balance);

        return modelMapper.map(saveBalance, BalanceDTO.Response.class);
    }
}
