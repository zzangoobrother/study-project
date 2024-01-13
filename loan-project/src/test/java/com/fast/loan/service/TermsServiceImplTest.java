package com.fast.loan.service;

import com.fast.loan.domain.Terms;
import com.fast.loan.dto.TermsDTO;
import com.fast.loan.repository.TermsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermsServiceImplTest {

    @InjectMocks
    TermsServiceImpl termsService;

    @Mock
    private TermsRepository termsRepository;

    @Spy
    private ModelMapper modelMapper;

    @Test
    void should_ReturnResponseOfNewTermsEntity_When_RequestTerms() {
        Terms entity = Terms.builder()
                .name("대출 이용 약관")
                .termsDetailUrl("https://abs.avd.com")
                .build();

        TermsDTO.Request request = TermsDTO.Request.builder()
                .name("대출 이용 약관")
                .termsDetailUrl("https://abs.avd.com")
                .build();

        when(termsRepository.save(ArgumentMatchers.any(Terms.class))).thenReturn(entity);

        TermsDTO.Response response = termsService.create(request);

        assertThat(response.getName()).isSameAs(entity.getName());
        assertThat(response.getTermsDetailUrl()).isSameAs(entity.getTermsDetailUrl());
    }

    @Test
    void should_ReturnAllResponseOfExistTermsEntites_When_RequestTermsList() {
        Terms entity1 = Terms.builder()
                .name("대출 이용약관 1")
                .termsDetailUrl("https://abs.avd.com")
                .build();

        Terms entity2 = Terms.builder()
                .name("대출 이용약관 2")
                .termsDetailUrl("https://abs.avd.com")
                .build();

        List<Terms> termsList = new ArrayList<>(Arrays.asList(entity1, entity2));

        when(termsRepository.findAll()).thenReturn(termsList);

        List<TermsDTO.Response> responses = termsService.getAll();

        assertThat(termsList.size()).isSameAs(2);
    }
}
