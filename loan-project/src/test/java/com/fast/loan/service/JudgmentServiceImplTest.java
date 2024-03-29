package com.fast.loan.service;

import com.fast.loan.domain.Application;
import com.fast.loan.domain.Judgment;
import com.fast.loan.dto.ApplicationDTO;
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

    @Test
    void should_ReturnResponseOfExistJudgmentEntity_When_RequestExistJudgmentId() {
        Judgment judgment = Judgment.builder()
                .judgmentId(1L)
                .build();

        when(judgmentRepository.findById(1L)).thenReturn(Optional.ofNullable(judgment));

        JudgmentDTO.Response response = judgmentService.get(1L);

        assertThat(response.getJudgmentId()).isSameAs(judgment.getJudgmentId());
    }

    @Test
    void should_ReturnResponseOfExistJudgmentEntity_When_RequestExistApplicationId() {
        Judgment judgment = Judgment.builder()
                .judgmentId(1L)
                .applicationId(1L)
                .build();

        Application application = Application.builder()
                .applicationId(1L)
                .build();

        when(applicationRepository.findById(1L)).thenReturn(Optional.ofNullable(application));
        when(judgmentRepository.findByApplicationId(1L)).thenReturn(Optional.ofNullable(judgment));

        JudgmentDTO.Response response = judgmentService.getJudgmentOfApplication(1L);

        assertThat(response.getJudgmentId()).isSameAs(judgment.getJudgmentId());
        assertThat(response.getApplicationId()).isSameAs(application.getApplicationId());
    }

    @Test
    void should_ReturnUpdateResponseOfExistJudgmentEntity_When_RequestUpdateExistJudgmentInfo() {
        Judgment judgment = Judgment.builder()
                .judgmentId(1L)
                .name("Member Choi")
                .approvalAmount(BigDecimal.valueOf(5000000))
                .build();

        JudgmentDTO.Request request = JudgmentDTO.Request.builder()
                .name("Member Lee")
                .approvalAmount(BigDecimal.valueOf(10000000))
                .build();

        when(judgmentRepository.findById(1L)).thenReturn(Optional.ofNullable(judgment));
        when(judgmentRepository.save(ArgumentMatchers.any(Judgment.class))).thenReturn(judgment);

        JudgmentDTO.Response response = judgmentService.update(1L, request);

        assertThat(response.getJudgmentId()).isSameAs(judgment.getJudgmentId());
        assertThat(response.getName()).isSameAs(request.getName());
        assertThat(response.getApprovalAmount()).isSameAs(request.getApprovalAmount());
    }

    @Test
    void should_DeletedJudgmentEntity_When_RequestDeleteExistJudgmentInfo() {
        Judgment judgment = Judgment.builder()
                .judgmentId(1L)
                .build();

        when(judgmentRepository.findById(1L)).thenReturn(Optional.ofNullable(judgment));
        when(judgmentRepository.save(ArgumentMatchers.any(Judgment.class))).thenReturn(judgment);

        judgmentService.delete(1L);

        assertThat(judgment.getIsDeleted()).isTrue();
    }

    @Test
    void should_ReturnUpdateResponseOfExistApplicationEntity_When_RequestGrantApprovalAmountOfJudgmentInfo() {
        Judgment judgment = Judgment.builder()
                .judgmentId(1L)
                .applicationId(1L)
                .approvalAmount(BigDecimal.valueOf(5000000))
                .build();

        Application application = Application.builder()
                .applicationId(1L)
                .build();

        when(judgmentRepository.findById(1L)).thenReturn(Optional.ofNullable(judgment));
        when(applicationRepository.findById(1L)).thenReturn(Optional.ofNullable(application));
        when(applicationRepository.save(ArgumentMatchers.any(Application.class))).thenReturn(application);

        ApplicationDTO.GrantAmount response = judgmentService.grant(1L);

        assertThat(response.getApplicationId()).isSameAs(application.getApplicationId());
        assertThat(response.getApprovalAmount()).isSameAs(judgment.getApprovalAmount());
    }
}
