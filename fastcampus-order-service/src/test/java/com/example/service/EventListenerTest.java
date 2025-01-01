package com.example.service;

import com.example.dto.DecreaseStockCountDto;
import com.example.dto.DeliveryStatusUpdate;
import com.example.dto.PaymentResult;
import com.example.entity.ProductOrder;
import com.example.enums.OrderStatus;
import com.example.feign.CatalogClient;
import com.example.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@Import(EventListener.class)
class EventListenerTest {

    @SpyBean
    private OrderRepository orderRepository;

    @MockBean
    private CatalogClient catalogClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EventListener eventListener;

    @Test
    void consumePaymentResult() throws JsonProcessingException {
        Long productId = 111L;
        Long paymentId = 222L;

        ProductOrder order = new ProductOrder(1L, productId, 1L, OrderStatus.INITIATED, null, null, "경기도 남양주시");
        orderRepository.save(order);

        Map<String, Object> catalogResponse = new HashMap<>();
        catalogResponse.put("name", "Hello TV");
        when(catalogClient.getProduct(productId)).thenReturn(catalogResponse);

        PaymentResult paymentResult = new PaymentResult(order.getId(), paymentId);
        ObjectMapper mapper = new ObjectMapper();
        String message = mapper.writeValueAsString(paymentResult);
        eventListener.consumePaymentResult(message);

        verify(kafkaTemplate, times(1)).send(eq("delivery_request"), any(String.class));
        assertEquals(paymentId, order.getPaymentId());
    }

    @Captor
    ArgumentCaptor<DecreaseStockCountDto> captor;

    @Test
    void consumeDeliveryStatusUpdate() throws JsonProcessingException {
        Long productId = 111L;
        Long deliveryId = 333L;
        Long productCount = 10L;

        ProductOrder productOrder = new ProductOrder(1L, productId, productCount, OrderStatus.INITIATED, null, null, "경기도 남양주시");
        ProductOrder order = orderRepository.save(productOrder);

        DeliveryStatusUpdate requested = new DeliveryStatusUpdate(order.getId(), deliveryId, "REQUESTED");
        ObjectMapper mapper = new ObjectMapper();
        String message = mapper.writeValueAsString(requested);
        eventListener.consumeDeliveryStatusUpdate(message);

        assertEquals(deliveryId, order.getDeliveryId());
        verify(catalogClient, times(1)).decreaseStockCount(eq(productId), captor.capture());
        assertEquals(productCount, captor.getValue().decreaseCount());
    }

    @Test
    void consumeDeliveryStatusUpdate_not_REQUESTED() throws JsonProcessingException {
        DeliveryStatusUpdate requested = new DeliveryStatusUpdate(1L, 10L, "IN_DELIVERY");
        ObjectMapper mapper = new ObjectMapper();
        String message = mapper.writeValueAsString(requested);
        eventListener.consumeDeliveryStatusUpdate(message);

        verify(orderRepository, times(0)).save(any());
        verify(catalogClient, times(0)).decreaseStockCount(any(), any());
    }
}
