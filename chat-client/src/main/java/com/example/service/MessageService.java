package com.example.service;

import com.example.dto.domain.Message;
import com.example.dto.domain.MessageSeqId;
import com.example.dto.websocket.inbound.FetchMessageResponse;
import com.example.dto.websocket.inbound.MessageNotification;
import com.example.dto.websocket.inbound.WriteMessageAck;
import com.example.dto.websocket.outbound.FetchMessageRequest;
import com.example.dto.websocket.outbound.WriteMessage;
import com.example.util.JsonUtil;
import jakarta.websocket.Session;

import java.util.Map;
import java.util.concurrent.*;

public class MessageService {

    private final int LIMIT_RETRIES = 3;
    private final long TIMEOUT_MS = 3000L;

    private final UserService userService;
    private final TerminalService terminalService;
    private final Map<Long, CompletableFuture<WriteMessageAck>> pendingMessages = new ConcurrentHashMap<>();
    private final Map<MessageSeqIdRange, ScheduledFuture<?>> scheduledFetchMessageRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private WebSocketService webSocketService;

    public MessageService(UserService userService, TerminalService terminalService) {
        this.userService = userService;
        this.terminalService = terminalService;
    }

    public void receiveMessage(WriteMessageAck writeMessageAck) {
        CompletableFuture<WriteMessageAck> future = pendingMessages.get(writeMessageAck.getSerial());
        if (future != null) {
            future.complete(writeMessageAck);
        }
    }

    public void receiveMessage(MessageNotification messageNotification) {
        if (userService.isInLobby() || !userService.getChannelId().equals(messageNotification.getChannelId())) {
            terminalService.printSystemMessage("Invalid channelId. Ignore message.");
            return;
        }

        MessageSeqId lastReadMessageSeqId = userService.getLastReadMessageSeqId();
        MessageSeqId receivedMessageSeqId = messageNotification.getMessageSeqId();
        if (lastReadMessageSeqId == null || receivedMessageSeqId.id() == lastReadMessageSeqId.id() + 1) {
            for (MessageSeqIdRange idRange : scheduledFetchMessageRequests.keySet()) {
                if (receivedMessageSeqId.id() >= idRange.start.id() && receivedMessageSeqId.id() <= idRange.end.id()) {
                    scheduledFetchMessageRequests.get(idRange).cancel(false);
                    scheduledFetchMessageRequests.remove(idRange);
                    if (!idRange.start.equals(idRange.end)) {
                        reserveFetchMessageRequest(idRange.start, idRange.end);
                    }
                }
            }

            userService.addMessage(new Message(messageNotification.getChannelId(), messageNotification.getMessageSeqId(), messageNotification.getUsername(), messageNotification.getContent()));
            processMessageBuffer();
        } else if (receivedMessageSeqId.id() > lastReadMessageSeqId.id() + 1) {
            userService.addMessage(new Message(messageNotification.getChannelId(), messageNotification.getMessageSeqId(), messageNotification.getUsername(), messageNotification.getContent()));
            reserveFetchMessageRequest(lastReadMessageSeqId, receivedMessageSeqId);
        } else if (receivedMessageSeqId.id() <= lastReadMessageSeqId.id()) {
            terminalService.printSystemMessage("Ignore duplication message : " + messageNotification.getMessageSeqId());
        }
    }

    public void receiveMessage(FetchMessageResponse fetchMessageResponse) {
        if (userService.isInLobby() || !userService.getChannelId().equals(fetchMessageResponse.getChannelId())) {
            terminalService.printSystemMessage("Invalid channelId. Ignore fetch messages.");
            return;
        }

        fetchMessageResponse.getMessages().forEach(userService::addMessage);
        processMessageBuffer();
    }

    public void sendMessage(Session session, WriteMessage message) {
        sendMessage(session, message, 0);
    }

    public void sendMessage(FetchMessageRequest fetchMessageRequest) {
        webSocketService.sendMessage(fetchMessageRequest);
    }

    private void sendMessage(Session session, WriteMessage message, int retryCount) {
        if (session != null && session.isOpen()) {
            CompletableFuture<WriteMessageAck> future = new CompletableFuture<>();
            future.orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                            .whenCompleteAsync((response, throwable) -> {
                                if (response != null) {
                                    userService.setLastReadMessageSeqId(response.getMessageSeqId());
                                    terminalService.printMessage("<me>", message.getContent());
                                    pendingMessages.remove(message.getSerial());
                                } else if (throwable instanceof TimeoutException && retryCount < LIMIT_RETRIES){
                                    sendMessage(session, message, retryCount + 1);
                                } else {
                                    terminalService.printSystemMessage("Send message failed.");
                                    pendingMessages.remove(message.getSerial());
                                }
                            });

            pendingMessages.put(message.getSerial(), future);

            JsonUtil.toJson(message).ifPresent(payload -> session.getAsyncRemote().sendText(payload, result -> {
                if (!result.isOK()) {
                    terminalService.printSystemMessage("'%s' send failed. cause : %s".formatted(payload, result.getException()));
                }
            }));
        }
    }

    private void processMessageBuffer() {
        while (!userService.isBufferEmpty()) {
            Message peekMessage = userService.peekMessage();
            if (userService.getLastReadMessageSeqId() == null || peekMessage.messageSeqId().id() == userService.getLastReadMessageSeqId().id() + 1) {
                Message message = userService.popMessage();
                terminalService.printMessage(message.username(), message.content());
            } else if (peekMessage.messageSeqId().id() <= userService.getLastReadMessageSeqId().id()) {
                userService.popMessage();
            } else if (peekMessage.messageSeqId().id() > userService.getLastReadMessageSeqId().id() + 1) {
                break;
            }
        }
    }

    private void reserveFetchMessageRequest(MessageSeqId lastReadSeqId, MessageSeqId receivedSeqId) {
        MessageSeqIdRange messageSeqIdRange = new MessageSeqIdRange(new MessageSeqId(lastReadSeqId.id() + 1), new MessageSeqId(receivedSeqId.id() - 1));
        ScheduledFuture<?> scheduledFuture = scheduledExecutorService.schedule(() -> {
            webSocketService.sendMessage(new FetchMessageRequest(userService.getChannelId(), messageSeqIdRange.start, messageSeqIdRange.end));
            scheduledFetchMessageRequests.remove(messageSeqIdRange);
        }, 100, TimeUnit.MILLISECONDS);

        scheduledFetchMessageRequests.put(messageSeqIdRange, scheduledFuture);
    }

    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    private record MessageSeqIdRange(MessageSeqId start, MessageSeqId end) {}
}
