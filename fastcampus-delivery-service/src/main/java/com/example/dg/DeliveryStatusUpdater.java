package com.example.dg;

import com.example.dto.DeliveryStatusUpdateDto;
import com.example.entity.Delivery;
import com.example.enums.DeliveryStatus;
import com.example.repository.DeliveryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeliveryStatusUpdater {

    private final DeliveryRepository deliveryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DeliveryStatusUpdater(DeliveryRepository deliveryRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.deliveryRepository = deliveryRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 10000)
    public void deliveryStatusUpdate() {
        List<Delivery> deliveryList = deliveryRepository.findAllByDeliveryStatus(DeliveryStatus.IN_DELIVERY);
        deliveryList.forEach(delivery -> {
            delivery.updateStatus(DeliveryStatus.COMPLETED);
            deliveryRepository.save(delivery);
            try {
                publishStatusChange(delivery);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        List<Delivery> requestedList = deliveryRepository.findAllByDeliveryStatus(DeliveryStatus.REQUESTED);
        requestedList.forEach(delivery -> {
            delivery.updateStatus(DeliveryStatus.IN_DELIVERY);
            deliveryRepository.save(delivery);
            try {
                publishStatusChange(delivery);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void publishStatusChange(Delivery delivery) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        DeliveryStatusUpdateDto deliveryStatusUpdateDto = new DeliveryStatusUpdateDto(delivery.getOrderId(), delivery.getId(), delivery.getDeliveryStatus());
        kafkaTemplate.send("delivery_status_update", mapper.writeValueAsString(deliveryStatusUpdateDto));
    }
}
