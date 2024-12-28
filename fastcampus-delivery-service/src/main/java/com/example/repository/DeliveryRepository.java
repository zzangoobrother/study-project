package com.example.repository;

import com.example.entity.Delivery;
import com.example.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findAllByOrderId(Long orderId);

    List<Delivery> findAllByDeliveryStatus(DeliveryStatus deliveryStatus);
}
