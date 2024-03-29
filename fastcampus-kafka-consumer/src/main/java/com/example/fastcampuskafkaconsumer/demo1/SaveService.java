package com.example.fastcampuskafkaconsumer.demo1;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SaveService {

    private final ShipmentCheckOutRepository shipmentCheckOutRepository;

    private final ModelMapper modelMapper = new ModelMapper();

    public Long saveCheckOutData(CheckOutDto checkOutDto) {
        ShipmentCheckOutEntity shipmentCheckOutEntity = modelMapper.map(checkOutDto, ShipmentCheckOutEntity.class);
        ShipmentCheckOutEntity saveEntity = shipmentCheckOutRepository.save(shipmentCheckOutEntity);

        return saveEntity.getShipmentId();
    }
}
