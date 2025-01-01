package com.example.service;

import com.example.dto.DeliveryStatusUpdateDto;
import com.example.dto.ProcessDeliveryDto;
import com.example.entity.Delivery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EventListener {

    private final DeliveryService deliveryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventListener(DeliveryService deliveryService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.deliveryService = deliveryService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "delivery_request")
    public void consumeDeliveryRequest(String message) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ProcessDeliveryDto dto = mapper.readValue(message, ProcessDeliveryDto.class);
        Delivery delivery = deliveryService.processDelivery(dto.orderId(), dto.productName(), dto.productCount(), dto.address());

        DeliveryStatusUpdateDto deliveryStatusUpdateDto = new DeliveryStatusUpdateDto(delivery.getOrderId(), delivery.getId(), delivery.getDeliveryStatus());
        kafkaTemplate.send("delivery_status_update", mapper.writeValueAsString(deliveryStatusUpdateDto));
    }
}
