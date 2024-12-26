package com.example.controller;

import com.example.dto.PaymentMethodDto;
import com.example.dto.ProcessPaymentDto;
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

    @PostMapping("/payments/process-payment")
    public Payment processPayment(@RequestBody ProcessPaymentDto dto) {
        return paymentService.processPayment(dto.userId(), dto.orderId(), dto.amountKRW(), dto.paymentMethodId());
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
