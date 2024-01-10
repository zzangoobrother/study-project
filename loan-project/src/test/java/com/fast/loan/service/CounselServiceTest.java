package com.fast.loan.service;

import com.fast.loan.domain.Counsel;
import com.fast.loan.dto.CounselDTO;
import com.fast.loan.repository.CounselRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounselServiceTest {

    @InjectMocks
    CounselServiceImpl counselService;

    @Mock
    private CounselRepository counselRepository;

    @Spy
    private ModelMapper modelMapper;

    @Test
    void Should_returnResponseOfNewCounselEntity_When_RequestCounsel() {
        Counsel counsel = Counsel.builder()
                .name("Member Choi")
                .cellPhone("010-1111-1222")
                .email("abde@adf.com")
                .memo("저는 대출을 받고 싶어요")
                .zipCode("12345")
                .address("서울특별시 어딘구 어딘동")
                .addressDetail("101동 101호")
                .build();

        CounselDTO.Request request = CounselDTO.Request.builder()
                .name("Member Choi")
                .cellPhone("010-1111-1222")
                .email("abde@adf.com")
                .memo("저는 대출을 받고 싶어요")
                .zipCode("12345")
                .address("서울특별시 어딘구 어딘동")
                .addressDetail("101동 101호")
                .build();

        when(counselRepository.save(any(Counsel.class))).thenReturn(counsel);

        CounselDTO.Response actual = counselService.create(request);

        assertThat(actual.getName()).isSameAs(request.getName());
    }
}
