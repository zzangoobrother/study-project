package com.example.repository;

import com.example.domain.OrderDetailView;
import com.example.domain.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query(
            "SELECT new com.example.domain.OrderDetailView(o.orderId, o.customerId, c.customerName, o.amount, o.orderStatus, o.payType, o.createdAt) " +
                    "FROM Order o JOIN Customer c ON o.customerId = c.customerId"
    )
    List<OrderDetailView> findAllOrderDetailView();
}
