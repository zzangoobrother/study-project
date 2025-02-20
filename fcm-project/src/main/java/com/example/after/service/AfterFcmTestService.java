package com.example.after.service;

import com.example.after.fcm.FcmClient;
import com.example.after.fcm.FcmSend;
import com.example.after.queue.Queue;
import com.example.model.Device;
import com.example.model.Message;
import com.example.model.MessageDevice;
import com.example.model.constant.MessageStatus;
import com.example.repository.DeviceRepository;
import com.example.repository.MessageDeviceRepository;
import com.example.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AfterFcmTestService {

    private final DeviceRepository deviceRepository;
    private final MessageRepository messageRepository;
    private final MessageDeviceRepository messageDeviceRepository;
    private final FcmClient fcmClient;
    private final FcmSend fcmSend;
    private final Queue queue;
    private final AsyncMultiProcessor asyncMultiProcessor;

    public AfterFcmTestService(DeviceRepository deviceRepository, MessageRepository messageRepository, MessageDeviceRepository messageDeviceRepository, @Qualifier("testFcmClient") FcmClient fcmClient, FcmSend fcmSend, Queue queue, AsyncMultiProcessor asyncMultiProcessor) {
        this.deviceRepository = deviceRepository;
        this.messageRepository = messageRepository;
        this.messageDeviceRepository = messageDeviceRepository;
        this.fcmClient = fcmClient;
        this.fcmSend = fcmSend;
        this.queue = queue;
        this.asyncMultiProcessor = asyncMultiProcessor;
    }

    @Transactional
    public void fcmSend(String title, String content) {
        Map<String, String> options = new HashMap<>();
        options.put("TYPE", "test");

//        List<Device> devices = createdToken();
        List<Device> devices = deviceRepository.findAll();
        log.info("token size : {}", devices.size());

        Message message = messageRepository.save(new Message(title, content));

        List<MessageDevice> messageDevices = devices.stream()
                .map(it -> MessageDevice.builder()
                        .messageId(message.getId())
                        .deviceId(it.getId())
                        .messageStatus(MessageStatus.WAITING)
                        .retryCount(0)
                        .option(options)
                        .build())
                .toList();
        messageDeviceRepository.saveAll(messageDevices);

        // queue.add(message);

        asyncMultiProcessor.produce(message);

//        log.info("queue size : {}", Queue.size());
    }

    private List<Device> createdToken() {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            tokens.add("test" + i);
        }

        List<Device> devices = tokens.stream()
                .map(Device::new)
                .toList();

        deviceRepository.saveAll(devices);

        return devices;
    }
}
