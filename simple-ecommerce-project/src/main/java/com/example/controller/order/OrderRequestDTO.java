package com.example.controller.order;

import com.example.enums.PayType;
import lombok.Getter;

@Getter
public class OrderRequestDTO {
    private PayType payType;
}
