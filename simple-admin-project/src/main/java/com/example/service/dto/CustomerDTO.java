package com.example.service.dto;

import com.example.enums.CustomerGrade;
import lombok.Builder;

import java.time.OffsetDateTime;

public record CustomerDTO(
        Long customerId,
        String customerName,
        String phoneNumber,
        String address,
        CustomerGrade customerGrade,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    @Builder
    public CustomerDTO {}
}
