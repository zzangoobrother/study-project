package com.example.service;

import com.example.dto.ProcessPaymentDto;
import com.example.dto.ProductOrderDetailDto;
import com.example.dto.StartOrderResponseDto;
import com.example.entity.ProductOrder;
import com.example.enums.OrderStatus;
import com.example.feign.CatalogClient;
import com.example.feign.DeliveryClient;
import com.example.feign.PaymentClient;
import com.example.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final DeliveryClient deliveryClient;
    private final CatalogClient catalogClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderService(OrderRepository orderRepository, PaymentClient paymentClient, DeliveryClient deliveryClient, CatalogClient catalogClient, KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.deliveryClient = deliveryClient;
        this.catalogClient = catalogClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    public StartOrderResponseDto startOrder(Long userId, Long productId, Long count) {
        Map<String, Object> product = catalogClient.getProduct(productId);
        Map<String, Object> paymentMethod = paymentClient.getPaymentMethod(userId);
        Map<String, Object> address = deliveryClient.getUserAddress(userId);

        ProductOrder order = new ProductOrder(userId, productId, count, OrderStatus.INITIATED, null, null, null);
        orderRepository.save(order);

        return new StartOrderResponseDto(order.getId(), paymentMethod, address);
    }

    public ProductOrder finishOrder(Long orderId, Long paymentMethodId, Long addressId) throws JsonProcessingException {
        ProductOrder order = orderRepository.findById(orderId).orElseThrow();

        Map<String, Object> product = catalogClient.getProduct(order.getProductId());

        ProcessPaymentDto processPaymentDto = new ProcessPaymentDto(orderId, order.getUserId(), Long.parseLong(product.get("price").toString()) * order.getCount(), paymentMethodId);
        ObjectMapper mapper = new ObjectMapper();
        kafkaTemplate.send("payment_result", mapper.writeValueAsString(processPaymentDto));

        Map<String, Object> address = deliveryClient.getAddress(addressId);
        order.update(OrderStatus.PAYMENT_REQUESTED, address.get("address").toString());
        return orderRepository.save(order);
    }

    public List<ProductOrder> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public ProductOrderDetailDto getOrderDetail(Long orderId) {
        ProductOrder order = orderRepository.findById(orderId).orElseThrow();
        Map<String, Object> payment = paymentClient.getPayment(order.getPaymentId());
        Map<String, Object> delivery = deliveryClient.getDelivery(order.getDeliveryId());

        return new ProductOrderDetailDto(orderId, order.getUserId(), order.getProductId(), order.getPaymentId(), order.getDeliveryId(), order.getOrderStatus(), payment.get("paymentStatus").toString(), delivery.get("status").toString());
    }
}
