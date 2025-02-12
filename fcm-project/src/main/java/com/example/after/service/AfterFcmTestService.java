package com.example.after.service;

import com.example.after.fcm.FcmClient;
import com.example.after.model.Device;
import com.example.after.model.Message;
import com.example.after.model.MessageDevice;
import com.example.after.model.constant.MessageStatus;
import com.example.after.queue.Queue;
import com.example.after.repository.DeviceRepository;
import com.example.after.repository.MessageDeviceRepository;
import com.example.after.repository.MessageRepository;
import com.example.dto.FcmMessage;
import com.example.dto.FcmMulticastMessage;
import lombok.RequiredArgsConstructor;
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

    public AfterFcmTestService(DeviceRepository deviceRepository, MessageRepository messageRepository, MessageDeviceRepository messageDeviceRepository, @Qualifier("testFcmClient") FcmClient fcmClient) {
        this.deviceRepository = deviceRepository;
        this.messageRepository = messageRepository;
        this.messageDeviceRepository = messageDeviceRepository;
        this.fcmClient = fcmClient;
    }

    @Transactional
    public void fcmSend(String title, String content) {
        Map<String, String> options = new HashMap<>();
        options.put("TYPE", "test");

        List<Device> devices = createdToken();
        log.info("token size : {}", devices.size());

        Message message = messageRepository.save(new Message(title, content, options));

        List<MessageDevice> messageDevices = devices.stream()
                .map(it -> MessageDevice.builder()
                        .messageId(message.getId())
                        .deviceId(it.getId())
                        .messageStatus(MessageStatus.WAITING)
                        .retryCount(0)
                        .build())
                .toList();
        messageDeviceRepository.saveAll(messageDevices);

//        List<FcmMessage> fcmMessages = devices.stream()
//                .map(it -> FcmMessage.builder()
//                        .notification(FcmMessage.Notification.builder()
//                                .title("test title")
//                                .body("test content")
//                                .build())
//                        .token(it.getToken())
//                        .options(options)
//                        .build())
//                .toList();

        List<String> tokens = devices.stream()
                .map(Device::getToken)
                .toList();

        fcmClient.send(FcmMulticastMessage.builder()
                .notification(FcmMulticastMessage.Notification.builder()
                        .title("test title")
                        .body("test content")
                        .build())
                .token(tokens)
                .options(options)
                .build());

//        Queue.addAll(fcmMessages);

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
