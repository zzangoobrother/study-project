package com.example.domain.order;

import com.example.controller.dto.customer.CustomerDTO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public class OrderSheet {
    private final Long orderId;
    private final OffsetDateTime orderDate;
    private final CustomerDTO customerDTO;
    private final String deliveryAddress;
    private final BigDecimal totalAmount;
    private final List<OrderItemDetail> orderItemDetails;

    public OrderSheet(Long orderId, OffsetDateTime orderDate, CustomerDTO customerDTO, String deliveryAddress, BigDecimal totalAmount, List<OrderItemDetail> orderItemDetails) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.customerDTO = customerDTO;
        this.deliveryAddress = deliveryAddress;
        this.totalAmount = totalAmount;
        this.orderItemDetails = orderItemDetails;
    }

    public static OrderSheet of(Long orderId, OffsetDateTime createdAt, CustomerDTO customerDTO, String deliveryAddress, BigDecimal amount, List<OrderItemDetail> allOrderItemDetails) {
        return new OrderSheet(orderId, createdAt, customerDTO, deliveryAddress, amount, allOrderItemDetails);
    }
}
