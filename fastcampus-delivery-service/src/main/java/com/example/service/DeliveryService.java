package com.example.service;

import com.example.dg.DeliveryAdapter;
import com.example.entity.Delivery;
import com.example.entity.UserAddress;
import com.example.enums.DeliveryStatus;
import com.example.repository.DeliveryRepository;
import com.example.repository.UserAddressRepository;
import org.springframework.stereotype.Service;

@Service
public class DeliveryService {

    private final UserAddressRepository userAddressRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAdapter deliveryAdapter;

    public DeliveryService(UserAddressRepository userAddressRepository, DeliveryRepository deliveryRepository, DeliveryAdapter deliveryAdapter) {
        this.userAddressRepository = userAddressRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryAdapter = deliveryAdapter;
    }

    public UserAddress addUserAddress(Long userId, String address, String alias) {
        UserAddress userAddress = new UserAddress(userId, address, alias);
        return userAddressRepository.save(userAddress);
    }

    public Delivery processDelivery(Long orderId, String productName, Long productCount, String address) {
        Long refCode = deliveryAdapter.processDelivery(productName, productCount, address);
        Delivery delivery = new Delivery(orderId, productName, productCount, address, DeliveryStatus.REQUESTED, refCode);
        return deliveryRepository.save(delivery);
    }

    public Delivery getDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId).orElseThrow();
    }

    public UserAddress getAddress(Long addressId) {
        return userAddressRepository.findById(addressId).orElseThrow();
    }

    public UserAddress getUserAddress(Long userId) {
        return userAddressRepository.findAllByUserId(userId).stream().findFirst().orElseThrow();
    }
}
