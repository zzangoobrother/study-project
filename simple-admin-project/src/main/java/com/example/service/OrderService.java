package com.example.service;

import com.example.domain.OrderDetailView;
import com.example.domain.entity.Customer;
import com.example.domain.entity.Order;
import com.example.exception.NotFoundCustomerException;
import com.example.exception.NotFoundOrderException;
import com.example.repository.CustomerRepository;
import com.example.repository.OrderRepository;
import com.example.service.dto.OrderDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public OrderService(OrderRepository orderRepository, CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
    }

    public List<OrderDetailView> findAllOrderDetailView() {
        return orderRepository.findAllOrderDetailView();
    }

    public OrderDTO findById(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(
                () -> new NotFoundOrderException("Not found order info")
        );

        Customer customer = customerRepository.findById(order.getCustomerId()).orElseThrow(
                () -> new NotFoundCustomerException("주문 고객 정보를 찾을 수 없습니다.")
        );
        return OrderDTO.builder()
                .orderId(order.getOrderId())
                .amount(order.getAmount())
                .customerId(order.getCustomerId())
                .customerName(customer.getCustomerName())
                .payType(order.getPayType())
                .orderStatus(order.getOrderStatus())
                .orderDate(order.getCreatedAt())
                .build();
    }
}
