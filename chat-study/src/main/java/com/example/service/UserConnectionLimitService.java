package com.example.service;

import com.example.constants.UserConnectionStatus;
import com.example.dto.domain.UserId;
import com.example.entity.UserConnectionEntity;
import com.example.entity.UserEntity;
import com.example.repository.UserConnectionRepository;
import com.example.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Function;

@Service
public class UserConnectionLimitService {

    private final UserRepository userRepository;
    private final UserConnectionRepository userConnectionRepository;

    private int limitConnection = 1_000;

    public UserConnectionLimitService(UserRepository userRepository, UserConnectionRepository userConnectionRepository) {
        this.userRepository = userRepository;
        this.userConnectionRepository = userConnectionRepository;
    }

    @Transactional
    public void accept(UserId acceptorUserId, UserId inviterUserId) {
        Long firstUserId = Long.min(acceptorUserId.id(), inviterUserId.id());
        Long secondUserId = Long.max(acceptorUserId.id(), inviterUserId.id());

        UserEntity firstUserEntity = userRepository.findForUpdateByUserId(firstUserId).orElseThrow(() -> new EntityNotFoundException("Invalid userId : " + firstUserId));
        UserEntity secondUserEntity = userRepository.findForUpdateByUserId(secondUserId).orElseThrow(() -> new EntityNotFoundException("Invalid userId : " + secondUserId));

        UserConnectionEntity userConnectionEntity = userConnectionRepository.findByPartnerAUserIdAndPartnerBUserIdAndStatus(firstUserId, secondUserId, UserConnectionStatus.PENDING).orElseThrow(() -> new EntityNotFoundException("Invalid status."));

        Function<Long, String> getErrorMessage = userid -> userid.equals(acceptorUserId.id()) ? "Connection limit reached." : "Connection limit reached by the other user.";

        int firstConnectionCount = firstUserEntity.getConnectionCount();
        if (firstConnectionCount >= limitConnection) {
            throw new IllegalStateException(getErrorMessage.apply(firstUserId));
        }

        int secondConnectionCount = secondUserEntity.getConnectionCount();
        if (secondConnectionCount >= limitConnection) {
            throw new IllegalStateException(getErrorMessage.apply(secondUserId));
        }

        firstUserEntity.setConnectionCount(firstConnectionCount + 1);
        secondUserEntity.setConnectionCount(secondConnectionCount + 1);

        userConnectionEntity.setStatus(UserConnectionStatus.ACCEPTED);
    }

    @Transactional
    public void disconnect(UserId senderUserId, UserId partnerUserId) {
        Long firstUserId = Long.min(senderUserId.id(), partnerUserId.id());
        Long secondUserId = Long.max(senderUserId.id(), partnerUserId.id());

        UserEntity firstUserEntity = userRepository.findForUpdateByUserId(firstUserId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid userId : " + firstUserId));
        UserEntity secondUserEntity = userRepository.findForUpdateByUserId(secondUserId)
                .orElseThrow(() -> new EntityNotFoundException("Invalid userId : " + secondUserId));

        UserConnectionEntity userConnectionEntity = userConnectionRepository.findByPartnerAUserIdAndPartnerBUserIdAndStatus(firstUserId, secondUserId, UserConnectionStatus.ACCEPTED)
                .orElseThrow(() -> new EntityNotFoundException("Invalid status."));

        int firstConnectionCount = firstUserEntity.getConnectionCount();
        if (firstConnectionCount <= 0) {
            throw new IllegalStateException("Count is already zero. userId : " + firstUserId);
        }

        int secondConnectionCount = secondUserEntity.getConnectionCount();
        if (secondConnectionCount <= 0) {
            throw new IllegalStateException("Count is already zero. userId : " + secondUserId);
        }

        firstUserEntity.setConnectionCount(firstConnectionCount - 1);
        secondUserEntity.setConnectionCount(secondConnectionCount - 1);

        userConnectionEntity.setStatus(UserConnectionStatus.DISCONNECTED);
    }

    @Transactional(readOnly = true)
    public int getLimitConnection() {
        return limitConnection;
    }

    @Transactional(readOnly = true)
    public void setLimitConnection(int limitConnection) {
        this.limitConnection = limitConnection;
    }
}
