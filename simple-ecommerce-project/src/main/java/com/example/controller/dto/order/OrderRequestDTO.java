package com.example.controller.dto.order;

import com.example.enums.PayType;
import lombok.Getter;

@Getter
public class OrderRequestDTO {
    private PayType payType;
}
