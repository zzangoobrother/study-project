package com.example.service;

import com.example.dto.DecreaseStockCountDto;
import com.example.dto.DeliveryRequest;
import com.example.dto.DeliveryStatusUpdate;
import com.example.dto.PaymentResult;
import com.example.entity.ProductOrder;
import com.example.enums.OrderStatus;
import com.example.feign.CatalogClient;
import com.example.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Component
public class EventListener {

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "payment_result")
    public void consumePaymentResult(String message) throws JsonProcessingException {
        // 결제 정보 업데이트
        ObjectMapper mapper = new ObjectMapper();
        PaymentResult dto = mapper.readValue(message, PaymentResult.class);
        ProductOrder order = orderRepository.findById(dto.orderId()).orElseThrow();
        order.update(dto.paymentId(), OrderStatus.DELIVERY_REQUESTED);
        orderRepository.save(order);

        Map<String, Object> product = catalogClient.getProduct(order.getProductId());
        DeliveryRequest deliveryRequest = new DeliveryRequest(order.getId(), product.get("name").toString(), order.getCount(), order.getDeliveryAddress());
        kafkaTemplate.send("delivery_request", mapper.writeValueAsString(deliveryRequest));
    }

    @KafkaListener(topics = "delivery_status_update")
    public void consumeDeliveryStatusUpdate(String message) throws JsonProcessingException {
        // 결제 정보 업데이트
        ObjectMapper mapper = new ObjectMapper();
        DeliveryStatusUpdate dto = mapper.readValue(message, DeliveryStatusUpdate.class);

        if (dto.deliveryStatus().equals("REQUESTED")) {
            ProductOrder order = orderRepository.findById(dto.orderId()).orElseThrow();
            DecreaseStockCountDto decreaseStockCountDto = new DecreaseStockCountDto(order.getCount());
            catalogClient.decreaseStockCount(order.getProductId(), decreaseStockCountDto);
        }
    }
}
