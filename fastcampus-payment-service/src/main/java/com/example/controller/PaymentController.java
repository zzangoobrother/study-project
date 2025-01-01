package com.example.controller;

import com.example.dto.PaymentMethodDto;
import com.example.entity.Payment;
import com.example.entity.PaymentMethod;
import com.example.service.PaymentService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/methods")
    public PaymentMethod registerPaymentMethod(@RequestBody PaymentMethodDto dto) {
        return paymentService.registerPaymentMethod(dto.userId(), dto.paymentMethodType(), dto.creditCardNumber());
    }

    @PostMapping("/payments/users/{userId}/first-method")
    public PaymentMethod getPaymentMethod(@PathVariable Long userId) {
        return paymentService.getPaymentMethodByUser(userId);
    }

    @PostMapping("/payments/{paymentId}")
    public Payment getPayment(@PathVariable Long paymentId) {
        return paymentService.getPayment(paymentId);
    }
}
