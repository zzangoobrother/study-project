package com.fast.loan.service;

import com.fast.loan.domain.Counsel;
import com.fast.loan.dto.CounselDTO;
import com.fast.loan.repository.CounselRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CounselServiceImpl implements CounselService {

    private final ModelMapper modelMapper;
    private final CounselRepository counselRepository;

    @Override
    public CounselDTO.Response create(CounselDTO.Request request) {
        Counsel counsel = modelMapper.map(request, Counsel.class);
        counsel.setAppliedAt(LocalDateTime.now());

        Counsel saveCounsel = counselRepository.save(counsel);
        return modelMapper.map(saveCounsel, CounselDTO.Response.class);
    }
}
