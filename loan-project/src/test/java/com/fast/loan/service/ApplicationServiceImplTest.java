package com.fast.loan.service;

import com.fast.loan.domain.AcceptTerms;
import com.fast.loan.domain.Application;
import com.fast.loan.domain.Terms;
import com.fast.loan.dto.ApplicationDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.repository.AcceptTermsRepository;
import com.fast.loan.repository.ApplicationRepository;
import com.fast.loan.repository.TermsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceImplTest {

    @InjectMocks
    private ApplicationServiceImpl applicationService;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private TermsRepository termsRepository;

    @Mock
    private AcceptTermsRepository acceptTermsRepository;

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

    @Test
    void should_ReturnDeletedApplicationEntity_When_RequestDeleteExistApplicationInfo() {
        Long targetId = 1L;

        Application entity = Application.builder()
                .applicationId(1L)
                .build();

        when(applicationRepository.save(ArgumentMatchers.any(Application.class))).thenReturn(entity);
        when(applicationRepository.findById(targetId)).thenReturn(Optional.ofNullable(entity));

        applicationService.delete(targetId);

        assertThat(entity.getIsDeleted()).isTrue();
    }

    @Test
    void should_AddAcceptTerms_When_RequestAcceptTermsOfApplication() {
        Terms terms1 = Terms.builder()
                .termsId(1L)
                .name("약관 1")
                .termsDetailUrl("https://abc.com")
                .build();

        Terms terms2 = Terms.builder()
                .termsId(2L)
                .name("약관 2")
                .termsDetailUrl("https://abc.com")
                .build();

        List<Long> acceptTerms = Arrays.asList(1L, 2L);

        ApplicationDTO.AcceptTerms request = ApplicationDTO.AcceptTerms.builder()
                .acceptTermsIds(acceptTerms)
                .build();

        Long findId = 1L;

        when(applicationRepository.findById(findId)).thenReturn(Optional.ofNullable(Application.builder().build()));
        when(termsRepository.findAll(Sort.by(Sort.Direction.ASC, "termsId"))).thenReturn(Arrays.asList(terms1, terms2));
        when(acceptTermsRepository.save(ArgumentMatchers.any(AcceptTerms.class))).thenReturn(AcceptTerms.builder().build());

        Boolean result = applicationService.acceptTerms(findId, request);

        assertThat(result).isTrue();
    }

    @Test
    void should_ThrowException_When_RequestNotAllAcceptTermsOfApplication() {
        Terms terms1 = Terms.builder()
                .termsId(1L)
                .name("약관 1")
                .termsDetailUrl("https://abc.com")
                .build();

        Terms terms2 = Terms.builder()
                .termsId(2L)
                .name("약관 2")
                .termsDetailUrl("https://abc.com")
                .build();

        List<Long> acceptTerms = Arrays.asList(1L);

        ApplicationDTO.AcceptTerms request = ApplicationDTO.AcceptTerms.builder()
                .acceptTermsIds(acceptTerms)
                .build();

        Long findId = 1L;

        when(applicationRepository.findById(findId)).thenReturn(Optional.ofNullable(Application.builder().build()));
        when(termsRepository.findAll(Sort.by(Sort.Direction.ASC, "termsId"))).thenReturn(Arrays.asList(terms1, terms2));

        assertThrows(BaseException.class, () -> applicationService.acceptTerms(findId, request));
    }

    @Test
    void whould_ThrowException_When_RequestNotExistAcceptTermsOfApplication() {
        Terms terms1 = Terms.builder()
                .termsId(1L)
                .name("약관 1")
                .termsDetailUrl("https://abc.com")
                .build();

        Terms terms2 = Terms.builder()
                .termsId(2L)
                .name("약관 2")
                .termsDetailUrl("https://abc.com")
                .build();

        List<Long> acceptTerms = Arrays.asList(1L, 3L);

        ApplicationDTO.AcceptTerms request = ApplicationDTO.AcceptTerms.builder()
                .acceptTermsIds(acceptTerms)
                .build();

        Long findId = 1L;

        when(applicationRepository.findById(findId)).thenReturn(Optional.ofNullable(Application.builder().build()));
        when(termsRepository.findAll(Sort.by(Sort.Direction.ASC, "termsId"))).thenReturn(Arrays.asList(terms1, terms2));

        assertThrows(BaseException.class, () -> applicationService.acceptTerms(findId, request));
    }
}
