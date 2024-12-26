package com.example.pg;

public interface CreditCardPaymentAdapter {
    Long processCreditPayment(Long amountKRW, String creditCardNumber);
}
