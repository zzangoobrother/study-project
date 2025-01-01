package com.example.service;

import com.example.dto.PaymentResult;
import com.example.dto.ProcessPaymentDto;
import com.example.entity.Payment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventListener {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventListener(PaymentService paymentService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "payment_request")
    public void consumePaymentRequest(String message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ProcessPaymentDto dto = mapper.readValue(message, ProcessPaymentDto.class);
        Payment payment = paymentService.processPayment(dto.userId(), dto.orderId(), dto.amountKRW(), dto.paymentMethodId());

        PaymentResult paymentResult = new PaymentResult(payment.getOrderId(), payment.getId(), payment.getPaymentStatus());
        kafkaTemplate.send("payment_result", mapper.writeValueAsString(paymentResult));
    }
}
