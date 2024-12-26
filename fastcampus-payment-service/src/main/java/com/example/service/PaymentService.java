package com.example.service;

import com.example.entity.Payment;
import com.example.entity.PaymentMethod;
import com.example.enums.PaymentMethodType;
import com.example.enums.PaymentStatus;
import com.example.pg.CreditCardPaymentAdapter;
import com.example.repository.PaymentMethodRepository;
import com.example.repository.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentRepository paymentRepository;
    private final CreditCardPaymentAdapter creditCardPaymentAdapter;

    public PaymentService(PaymentMethodRepository paymentMethodRepository, PaymentRepository paymentRepository, CreditCardPaymentAdapter creditCardPaymentAdapter) {
        this.paymentMethodRepository = paymentMethodRepository;
        this.paymentRepository = paymentRepository;
        this.creditCardPaymentAdapter = creditCardPaymentAdapter;
    }

    public PaymentMethod registerPaymentMethod(Long userId, PaymentMethodType paymentMethodType, String creditCardNumber) {
        PaymentMethod paymentMethod = new PaymentMethod(userId, paymentMethodType, creditCardNumber);
        return paymentMethodRepository.save(paymentMethod);
    }

    public Payment processPayment(Long userId, Long orderId, Long amountKRW, Long paymentMethodId) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(paymentMethodId).orElseThrow();
        if (paymentMethod.getPaymentMethodType() != PaymentMethodType.CREDIT_CARD) {
            throw new RuntimeException("Unsupported payment method type");
        }

        Long refCode = creditCardPaymentAdapter.processCreditPayment(amountKRW, paymentMethod.getCreditCardNumber());
        Payment payment = new Payment(userId, orderId, amountKRW, paymentMethod.getPaymentMethodType(), paymentMethod.getCreditCardNumber(), PaymentStatus.COMPLETED, refCode);
        return paymentRepository.save(payment);
    }

    public PaymentMethod getPaymentMethodByUser(Long userId) {
        return paymentMethodRepository.findByUserId(userId).stream()
                .findFirst()
                .orElseThrow();
    }

    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow();
    }
}
