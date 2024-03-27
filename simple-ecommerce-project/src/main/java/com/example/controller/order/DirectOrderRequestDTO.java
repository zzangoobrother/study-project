package com.example.controller.order;

import com.example.enums.PayType;
import lombok.Getter;

@Getter
public class DirectOrderRequestDTO {
    private Long productId;
    private PayType payType;
}
