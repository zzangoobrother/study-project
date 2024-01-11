package com.fast.loan.service;

import com.fast.loan.domain.Counsel;
import com.fast.loan.dto.CounselDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.exception.ResultType;
import com.fast.loan.repository.CounselRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void should_returnResponseOfNewCounselEntity_When_RequestCounsel() {
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

    @Test
    void should_ReturnResponseOfExistCounselEntity_When_RequestExistCounselId() {
        Long findId = 1L;

        Counsel counsel = Counsel.builder()
                .counselId(1L)
                .build();

        when(counselRepository.findById(findId)).thenReturn(Optional.ofNullable(counsel));

        CounselDTO.Response response = counselService.get(findId);

        assertThat(response.getCounselId()).isSameAs(findId);
    }

    @Test
    void should_ThrowException_When_RequestNotExistCounselId() {
        Long findId = 2L;

        when(counselRepository.findById(findId)).thenThrow(new BaseException(ResultType.SYSTEM_ERROR));

        assertThrows(BaseException.class, () -> counselService.get(findId));
    }
}
