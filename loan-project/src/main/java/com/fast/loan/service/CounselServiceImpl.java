package com.fast.loan.service;

import com.fast.loan.domain.Counsel;
import com.fast.loan.dto.CounselDTO;
import com.fast.loan.exception.BaseException;
import com.fast.loan.exception.ResultType;
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

    @Override
    public CounselDTO.Response get(Long counselId) {
        Counsel counsel = counselRepository.findById(counselId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        return modelMapper.map(counsel, CounselDTO.Response.class);
    }

    @Override
    public CounselDTO.Response update(Long counselId, CounselDTO.Request request) {
        Counsel counsel = counselRepository.findById(counselId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        counsel.setName(request.getName());
        counsel.setCellPhone(request.getCellPhone());
        counsel.setEmail(request.getEmail());
        counsel.setMemo(request.getMemo());
        counsel.setAddress(request.getAddress());
        counsel.setAddressDetail(request.getAddressDetail());
        counsel.setZipCode(request.getZipCode());

        counselRepository.save(counsel);

        return modelMapper.map(counsel, CounselDTO.Response.class);
    }

    @Override
    public void delete(Long counselId) {
        Counsel counsel = counselRepository.findById(counselId).orElseThrow(() -> new BaseException(ResultType.SYSTEM_ERROR));

        counsel.setIsDeleted(true);

        counselRepository.save(counsel);
    }
}
