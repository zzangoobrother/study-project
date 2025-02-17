package com.example.after.job;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.after.queue.Queue;
import com.example.model.Message;
import com.example.model.MessageDevice;
import com.example.model.constant.MessageStatus;
import com.example.repository.MessageDeviceRepository;
import com.example.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class MessageJob {

    private final MessageRepository messageRepository;
    private final MessageDeviceRepository messageDeviceRepository;
    private final Queue queue;

    // 알림 실패 재전송
    @Scheduled(fixedDelay = 60000)
    public void excutor() {
        List<MessageDevice> messageDevices = messageDeviceRepository.findAllByMessageStatusAndRetryCountLessThan(MessageStatus.WAITING, 5);

        Map<Long, List<MessageDevice>> messageDeviceMap = messageDevices.stream()
                .collect(Collectors.groupingBy(MessageDevice::getMessageId));

        Set<Long> messageIds = messageDeviceMap.keySet();
        List<Message> messages = messageRepository.findAllById(messageIds);
        messages.forEach(queue::add);
    }
}
