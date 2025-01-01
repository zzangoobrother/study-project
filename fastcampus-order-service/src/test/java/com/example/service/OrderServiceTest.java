package com.example.service;

import com.example.dto.ProductOrderDetailDto;
import com.example.dto.StartOrderResponseDto;
import com.example.entity.ProductOrder;
import com.example.enums.OrderStatus;
import com.example.feign.CatalogClient;
import com.example.feign.DeliveryClient;
import com.example.feign.PaymentClient;
import com.example.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@DataJpaTest
@Import(OrderService.class)
class OrderServiceTest {

    @SpyBean
    private OrderRepository orderRepository;

    @MockBean
    private PaymentClient paymentClient;

    @MockBean
    private DeliveryClient deliveryClient;

    @MockBean
    private CatalogClient catalogClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private OrderService orderService;

    @Test
    void startOrder() {
        Map<String, Object> paymentMethodRes = new HashMap<>();
        Map<String, Object> userAddressRes = new HashMap<>();
        paymentMethodRes.put("paymentMethodType", "CREDIT_CARD");
        userAddressRes.put("address", " 경기도 남양주시");


        when(paymentClient.getPaymentMethod(1L)).thenReturn(paymentMethodRes);
        when(deliveryClient.getUserAddress(1L)).thenReturn(userAddressRes);

        StartOrderResponseDto startOrderResponseDto = orderService.startOrder(1L, 1L, 2L);

        assertNotNull(startOrderResponseDto.orderId());
        assertEquals(paymentMethodRes, startOrderResponseDto.paymentMethod());
        assertEquals(userAddressRes, startOrderResponseDto.address());

        Optional<ProductOrder> order = orderRepository.findById(startOrderResponseDto.orderId());
        assertEquals(OrderStatus.INITIATED, order.get().getOrderStatus());
    }

    @Test
    void finishOrder() throws JsonProcessingException {
        ProductOrder order = new ProductOrder(1L, 1L, 1L, OrderStatus.INITIATED, null, null, null);
        orderRepository.save(order);

        final String address = "경기도 남양주시";
        Map<String, Object> catalogResponse = new HashMap<>();
        Map<String, Object> deliveryResponse = new HashMap<>();
        catalogResponse.put("price", "100");
        deliveryResponse.put("address", address);

        when(catalogClient.getProduct(1L)).thenReturn(catalogResponse);
        when(deliveryClient.getAddress(1L)).thenReturn(deliveryResponse);

        ProductOrder response = orderService.finishOrder(order.getId(), 1L, 1L);

        assertEquals(address, response.getDeliveryAddress());
        verify(kafkaTemplate, times(1)).send(eq("payment_result"), any(String.class));
    }

    @Test
    void getUserOrders() {
        final Long userId = 123L;

        ProductOrder order1 = new ProductOrder(userId, 100L, 1L, OrderStatus.INITIATED, null, null, null);
        ProductOrder order2 = new ProductOrder(userId, 110L, 1L, OrderStatus.INITIATED, null, null, null);

        orderRepository.save(order1);
        orderRepository.save(order2);

        List<ProductOrder> response = orderService.getUserOrders(userId);

        assertEquals(2, response.size());
        assertEquals(100L, response.get(0).getProductId());
        assertEquals(110L, response.get(1).getProductId());
    }

    @Test
    void getOrderDetail() {
        ProductOrder order = new ProductOrder(1L, 1L, 1L, OrderStatus.DELIVERY_REQUESTED, 10L, 11L, null);
        orderRepository.save(order);

        final String paymentStatus = "COMPLETED";
        final String deliveryStatus = "IN_DELIVERY";

        Map<String, Object> paymentResponse = new HashMap<>();
        Map<String, Object> deliveryResponse = new HashMap<>();
        paymentResponse.put("paymentStatus", paymentStatus);
        deliveryResponse.put("status", deliveryStatus);

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));
        when(paymentClient.getPayment(10L)).thenReturn(paymentResponse);
        when(deliveryClient.getDelivery(11L)).thenReturn(deliveryResponse);

        ProductOrderDetailDto response = orderService.getOrderDetail(1000L);

        assertEquals(10L, response.paymentId());
        assertEquals(11L, response.deliveryId());
        assertEquals(paymentStatus, response.paymentStatus());
        assertEquals(deliveryStatus, response.deliveryStatus());
    }
}
