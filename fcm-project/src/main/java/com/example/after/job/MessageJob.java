package com.example.after.job;

import com.example.after.fcm.AfterFcmClient;
import com.example.after.model.Device;
import com.example.after.model.Message;
import com.example.after.model.MessageDevice;
import com.example.after.model.constant.MessageStatus;
import com.example.after.repository.DeviceRepository;
import com.example.after.repository.MessageDeviceRepository;
import com.example.after.repository.MessageRepository;
import com.example.dto.FcmMulticastMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class MessageJob {

    private final MessageRepository messageRepository;
    private final DeviceRepository deviceRepository;
    private final MessageDeviceRepository messageDeviceRepository;
    private final AfterFcmClient fcmClient;

    // 알림 실패 재전송
    @Scheduled(fixedDelay = 60000)
    public void excutor() {
        List<MessageDevice> messageDevices = messageDeviceRepository.findAllByMessageStatusAndRetryCountLessThan(MessageStatus.WAITING, 5);

        Map<Long, List<MessageDevice>> messageDeviceMap = messageDevices.stream()
                .collect(Collectors.groupingBy(MessageDevice::getMessageId));

        Set<Long> messageIds = messageDeviceMap.keySet();
        Map<Long, Message> messageMap = messageRepository.findAllById(messageIds).stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));

        messageDeviceMap.values().forEach(it -> {
            Long messageId = it.get(0).getMessageId();

            List<Long> deviceIds = it.stream().map(MessageDevice::getDeviceId).toList();
            List<String> tokens = deviceRepository.findAllById(deviceIds).stream().map(Device::getToken).toList();

            fcmClient.send(FcmMulticastMessage.builder()
                    .notification(FcmMulticastMessage.Notification.builder()
                            .title(messageMap.get(messageId).getTitle())
                            .body(messageMap.get(messageId).getContent())
                            .build())
                    .token(tokens)
                    .options(messageMap.get(messageId).getOption())
                    .build());
        });
    }
}
