package com.example.after.service;

import com.example.after.fcm.FcmSend;
import com.example.dto.FcmMessage;
import com.example.model.Device;
import com.example.model.Message;
import com.example.model.MessageDevice;
import com.example.model.constant.MessageStatus;
import com.example.repository.DeviceRepository;
import com.example.repository.MessageDeviceRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class SendService {

    private final FcmSend fcmSend;
    private final DeviceRepository deviceRepository;
    private final MessageDeviceRepository messageDeviceRepository;

    @Transactional
    public void send(Message message) {
        List<MessageDevice> messageDevices = messageDeviceRepository.findByMessageIdAndMessageStatus(message.getId(), MessageStatus.WAITING);
        List<Long> deviceIds = messageDevices.stream()
                .map(MessageDevice::getDeviceId)
                .toList();
        List<Device> devices = deviceRepository.findAllById(deviceIds);

        Map<Long, MessageDevice> messageDeviceMap = messageDevices.stream()
                .collect(Collectors.toMap(MessageDevice::getDeviceId, Function.identity()));

        List<BatchResponse> batchResponseList = new ArrayList<>();
        devices.forEach(it -> {
            log.info("보낸다.");
            FcmMessage fcmMessage = FcmMessage.builder()
                    .notification(FcmMessage.Notification.builder()
                            .title(message.getTitle())
                            .body(message.getContent())
                            .build())
                    .token(it.getToken())
                    .options(messageDeviceMap.get(it.getId()).getOption())
                    .build();

            List<BatchResponse> batchResponses = fcmSend.send(fcmMessage);
            batchResponseList.addAll(batchResponses);
        });

        log.info("batch : {}", batchResponseList.size());
        for (BatchResponse batchResponse : batchResponseList) {
            List<SendResponse> sendResponses = batchResponse.getResponses();
            for (int i = 0; i < sendResponses.size(); i++) {
                MessageDevice messageDevice = messageDevices.get(i);
                SendResponse sendResponse = sendResponses.get(i);
                if (sendResponse.isSuccessful()) {
                    messageDevice.completed();
                    continue;
                }

                MessagingErrorCode messagingErrorCode = sendResponse.getException().getMessagingErrorCode();
                log.info("messagingErrorCode {} ", messagingErrorCode);
                // 재시도 처리
                if (MessagingErrorCode.QUOTA_EXCEEDED == messagingErrorCode
                        || MessagingErrorCode.UNAVAILABLE == messagingErrorCode
                        || MessagingErrorCode.INTERNAL == messagingErrorCode) {
                    log.info("재처리 {}", sendResponse.isSuccessful());
                    messageDevice.waiting();
                }
                // 없는 토큰으로 실패
                else if (MessagingErrorCode.UNREGISTERED == messagingErrorCode
                        || MessagingErrorCode.INVALID_ARGUMENT == messagingErrorCode) {
                    log.info("실패 {}", sendResponse.isSuccessful());
                    messageDevice.cancel();
                }
            }
        }
    }
}
