package com.fast.loan.service;

import com.fast.loan.domain.Application;
import com.fast.loan.domain.Judgment;
import com.fast.loan.dto.ApplicationDTO;
import com.fast.loan.dto.JudgmentDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.exception.ResultType;
import com.fast.loan.repository.ApplicationRepository;
import com.fast.loan.repository.JudgmentRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class JudgmentServiceImpl implements JudgmentService {

    private final JudgmentRepository judgmentRepository;
    private final ApplicationRepository applicationRepository;
    private final ModelMapper modelMapper;

    @Override
    public JudgmentDTO.Response create(JudgmentDTO.Request request) {
        Long applicationId = request.getApplicationId();
        if (!isPresentApplication(applicationId)) {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        }

        Judgment judgment = modelMapper.map(request, Judgment.class);
        Judgment saveJudgment = judgmentRepository.save(judgment);

        return modelMapper.map(saveJudgment, JudgmentDTO.Response.class);
    }

    @Override
    public JudgmentDTO.Response get(Long judgmentId) {
        Judgment judgment = judgmentRepository.findById(judgmentId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));
        return modelMapper.map(judgment, JudgmentDTO.Response.class);
    }

    @Override
    public JudgmentDTO.Response getJudgmentOfApplication(Long applicationId) {
        if (!isPresentApplication(applicationId)) {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        }

        Judgment judgment = judgmentRepository.findByApplicationId(applicationId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        return modelMapper.map(judgment, JudgmentDTO.Response.class);
    }

    @Override
    public JudgmentDTO.Response update(Long judgmentId, JudgmentDTO.Request request) {
        Judgment judgment = judgmentRepository.findById(judgmentId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        judgment.setName(request.getName());
        judgment.setApprovalAmount(request.getApprovalAmount());

        judgmentRepository.save(judgment);

        return modelMapper.map(judgment, JudgmentDTO.Response.class);
    }

    @Override
    public void delete(Long judgmentId) {
        Judgment judgment = judgmentRepository.findById(judgmentId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        judgment.setIsDeleted(true);

        judgmentRepository.save(judgment);
    }

    @Override
    public ApplicationDTO.GrantAmount grant(Long judgmentId) {
        Judgment judgment = judgmentRepository.findById(judgmentId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        Long applicationId = judgment.getApplicationId();
        Application application = applicationRepository.findById(applicationId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        BigDecimal approvalAmount = judgment.getApprovalAmount();
        application.setApprovalAmount(approvalAmount);
        applicationRepository.save(application);

        return modelMapper.map(application, ApplicationDTO.GrantAmount.class);
    }

    private boolean isPresentApplication(Long applicationId) {
        return applicationRepository.findById(applicationId).isPresent();
    }
}
