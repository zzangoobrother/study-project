package com.example.dg;

import com.example.entity.Delivery;
import com.example.enums.DeliveryStatus;
import com.example.repository.DeliveryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliveryStatusUpdater {

    private final DeliveryRepository deliveryRepository;

    public DeliveryStatusUpdater(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Scheduled(fixedDelay = 10000)
    public void deliveryStatusUpdate() {
        List<Delivery> deliveryList = deliveryRepository.findAllByDeliveryStatus(DeliveryStatus.IN_DELIVERY);
        deliveryList.forEach(delivery -> delivery.updateStatus(DeliveryStatus.COMPLETED));
        deliveryRepository.saveAll(deliveryList);

        List<Delivery> requestedList = deliveryRepository.findAllByDeliveryStatus(DeliveryStatus.REQUESTED);
        requestedList.forEach(delivery -> delivery.updateStatus(DeliveryStatus.IN_DELIVERY));
        deliveryRepository.saveAll(requestedList);
    }
}
