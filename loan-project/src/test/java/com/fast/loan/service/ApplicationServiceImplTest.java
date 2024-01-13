package com.fast.loan.service;

import com.fast.loan.domain.Application;
import com.fast.loan.dto.ApplicationDTO;
import com.fast.loan.repository.ApplicationRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    @Mock
    private ApplicationRepository applicationRepository;

    @Spy
    private ModelMapper mapper;

    @Test
    void should_ReturnResponseOfNewApplicationEntity_When_RequestCreateApplication() {
        Application entity = Application.builder()
                .name("Member Choi")
                .cellPhone("010-2222-1111")
                .email("mail@naver.com")
                .hopeAmount(BigDecimal.valueOf(5000000))
                .build();

        ApplicationDTO.Request request = ApplicationDTO.Request.builder()
                .name("Member Choi")
                .cellPhone("010-2222-1111")
                .email("mail@naver.com")
                .hopeAmount(BigDecimal.valueOf(5000000))
                .build();

        when(applicationRepository.save(ArgumentMatchers.any(Application.class))).thenReturn(entity);

        ApplicationDTO.Response response = applicationService.create(request);

        assertThat(response.getHopeAmount()).isSameAs(entity.getHopeAmount());
        assertThat(response.getName()).isSameAs(entity.getName());
    }

    @Test
    void should_ReturnResponsOfExistApplicationEntity_When_RequestExistApplicationId() {
        Long findId = 1L;

        Application entity = Application.builder()
                .applicationId(1L)
                .build();

        when(applicationRepository.findById(findId)).thenReturn(Optional.ofNullable(entity));

        ApplicationDTO.Response response = applicationService.get(findId);

        assertThat(response.getApplicationId()).isSameAs(findId);
    }

    @Test
    void should_ReturnUpdatedResponseOfExistApplicationEntity_When_RequestUpdateExstApplicationInfo() {
        Long findId = 1L;

        Application entity = Application.builder()
                .applicationId(1L)
                .hopeAmount(BigDecimal.valueOf(5000000))
                .build();

        ApplicationDTO.Request request = ApplicationDTO.Request.builder()
                .hopeAmount(BigDecimal.valueOf(1000000))
                .build();

        when(applicationRepository.save(ArgumentMatchers.any(Application.class))).thenReturn(entity);
        when(applicationRepository.findById(findId)).thenReturn(Optional.ofNullable(entity));

        ApplicationDTO.Response response = applicationService.update(findId, request);

        assertThat(response.getApplicationId()).isSameAs(findId);
        assertThat(response.getHopeAmount()).isSameAs(request.getHopeAmount());
    }
}
