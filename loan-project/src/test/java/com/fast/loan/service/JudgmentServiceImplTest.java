package com.fast.loan.service;

import com.fast.loan.domain.Application;
import com.fast.loan.domain.Judgment;
import com.fast.loan.dto.JudgmentDTO;
import com.fast.loan.repository.ApplicationRepository;
import com.fast.loan.repository.JudgmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgmentServiceImplTest {

    @InjectMocks
    JudgmentServiceImpl judgmentService;

    @Mock
    private JudgmentRepository judgmentRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Spy
    private ModelMapper modelMapper;

    @Test
    void should_ReturnResponseOfNewJudgmentEntity_When_RequestNewJudgment() {
        Judgment judgment = Judgment.builder()
                .applicationId(1L)
                .name("Member Choi")
                .approvalAmount(BigDecimal.valueOf(5000000))
                .build();

        JudgmentDTO.Request request = JudgmentDTO.Request.builder()
                .applicationId(1L)
                .name("Member Choi")
                .approvalAmount(BigDecimal.valueOf(5000000))
                .build();

        when(applicationRepository.findById(1L)).thenReturn(Optional.ofNullable(Application.builder().build()));
        when(judgmentRepository.save(ArgumentMatchers.any(Judgment.class))).thenReturn(judgment);

        JudgmentDTO.Response response = judgmentService.create(request);

        assertThat(response.getName()).isSameAs(judgment.getName());
        assertThat(response.getApplicationId()).isSameAs(judgment.getApplicationId());
        assertThat(response.getApprovalAmount()).isSameAs(judgment.getApprovalAmount());
    }
}
