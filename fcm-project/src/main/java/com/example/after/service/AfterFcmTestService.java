package com.example.after.service;

import com.example.after.fcm.AfterFcmClient;
import com.example.after.model.Device;
import com.example.after.model.Message;
import com.example.after.model.MessageDevice;
import com.example.after.model.constant.MessageStatus;
import com.example.after.repository.DeviceRepository;
import com.example.after.repository.MessageDeviceRepository;
import com.example.after.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AfterFcmTestService {

    private final AfterFcmClient fcmClient;
    private final DeviceRepository deviceRepository;
    private final MessageRepository messageRepository;
    private final MessageDeviceRepository messageDeviceRepository;

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
