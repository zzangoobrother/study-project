package com.fast.loan.service;

import com.fast.loan.domain.Terms;
import com.fast.loan.dto.TermsDTO;
import com.fast.loan.repository.TermsRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TermsServiceImpl implements TermsService {

    private final TermsRepository termsRepository;
    private final ModelMapper modelMapper;

    @Override
    public TermsDTO.Response create(TermsDTO.Request request) {
        Terms terms = modelMapper.map(request, Terms.class);
        Terms saveTerms = termsRepository.save(terms);

        return modelMapper.map(saveTerms, TermsDTO.Response.class);
    }

    @Override
    public List<TermsDTO.Response> getAll() {
        List<Terms> termsList = termsRepository.findAll();
        return termsList.stream()
                .map(terms -> modelMapper.map(terms, TermsDTO.Response.class))
                .toList();
    }
}
