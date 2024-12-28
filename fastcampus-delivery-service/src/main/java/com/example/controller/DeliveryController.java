package com.example.controller;

import com.example.dto.ProcessDeliveryDto;
import com.example.dto.RegisterAddressDto;
import com.example.entity.Delivery;
import com.example.entity.UserAddress;
import com.example.service.DeliveryService;
import org.springframework.web.bind.annotation.*;

@RestController
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/delivery/addresses")
    public UserAddress registerAddress(@RequestBody RegisterAddressDto dto) {
        return deliveryService.addUserAddress(dto.userId(), dto.address(), dto.alias());
    }

    @PostMapping("/delivery/process-delivery")
    public Delivery processDelivery(@RequestBody ProcessDeliveryDto dto) {
        return deliveryService.processDelivery(dto.orderId(), dto.productName(), dto.productCount(), dto.address());
    }

    @GetMapping("/delivery/deliveries/{deliveryId}")
    public Delivery getDelivery(@PathVariable Long deliveryId) {
        return deliveryService.getDelivery(deliveryId);
    }

    @GetMapping("/delivery/address/{addressId}")
    public UserAddress getAddress(@PathVariable Long addressId) {
        return deliveryService.getAddress(addressId);
    }

    @GetMapping("/delivery/users/{userId}/first-address")
    public UserAddress getUserAddress(@PathVariable Long userId) {
        return deliveryService.getUserAddress(userId);
    }
}
