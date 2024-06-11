package com.example.outboxPatternConsumerPractice.service;

import com.example.outboxPatternConsumerPractice.entity.Order;
import com.example.outboxPatternConsumerPractice.entity.OrderDetail;
import com.example.outboxPatternConsumerPractice.entity.Product;
import com.example.outboxPatternConsumerPractice.repository.OrderDetailRepository;
import com.example.outboxPatternConsumerPractice.repository.OrderRepository;
import com.example.outboxPatternConsumerPractice.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, OrderDetailRepository orderDetailRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.orderDetailRepository = orderDetailRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public void order(String code) {
        log.info("code : {}", code);
        Optional<Order> optionalOrder = orderRepository.findByCode(UUID.fromString(code));
        if (!optionalOrder.isPresent()) {
            return;
        }

        Order order = optionalOrder.get();
        List<OrderDetail> orderDetails = orderDetailRepository.findAllByOrderId(order.getId());
        List<Long> productIds = orderDetails.stream()
                .map(OrderDetail::getProductId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        orderDetails.forEach(it -> productMap.get(it.getProductId()).decrease(it.getQuantity()));
    }
}
