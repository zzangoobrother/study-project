package com.example.after.service;

import com.example.after.fcm.FcmSend;
import com.example.dto.FcmMessage;
import com.example.model.Device;
import com.example.model.Message;
import com.example.model.MessageDevice;
import com.example.repository.DeviceRepository;
import com.example.repository.MessageDeviceRepository;
import com.example.repository.MessageRepository;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class SendService {

    private final FcmSend fcmSend;
    private final MessageRepository messageRepository;
    private final DeviceRepository deviceRepository;
    private final MessageDeviceRepository messageDeviceRepository;

    public void send(Message message) {
        List<MessageDevice> messageDevices = messageDeviceRepository.findByMessageId(message.getId());
        List<Long> deviceIds = messageDevices.stream()
                .map(MessageDevice::getDeviceId)
                .toList();
        List<Device> devices = deviceRepository.findAllById(deviceIds);

        Map<Long, MessageDevice> messageDeviceMap = messageDevices.stream()
                .collect(Collectors.toMap(MessageDevice::getId, Function.identity()));
        List<FcmMessage> fcmMessages = devices.stream()
                .map(it -> FcmMessage.builder()
                        .notification(FcmMessage.Notification.builder()
                                .title("test title")
                                .body("test content")
                                .build())
                        .token(it.getToken())
                        .options(messageDeviceMap.get(it.getId()).getOption())
                        .build())
                .toList();

        List<BatchResponse> batchResponses = fcmSend.send(fcmMessages);

        for (BatchResponse batchResponse : batchResponses) {
            List<SendResponse> sendResponses = batchResponse.getResponses();
            for (int i = 0; i < sendResponses.size(); i++) {
                SendResponse sendResponse = sendResponses.get(i);
                if (sendResponse.isSuccessful()) {
                    continue;
                }

                MessagingErrorCode messagingErrorCode = sendResponse.getException().getMessagingErrorCode();
                // 재시도 처리
                if (MessagingErrorCode.QUOTA_EXCEEDED == messagingErrorCode
                        || MessagingErrorCode.UNAVAILABLE == messagingErrorCode
                        || MessagingErrorCode.INTERNAL == messagingErrorCode) {

                }
                // 없는 토큰으로 실패
                else if (MessagingErrorCode.UNREGISTERED == messagingErrorCode
                        || MessagingErrorCode.INVALID_ARGUMENT == messagingErrorCode) {

                }
            }
        }
    }
}
