package com.example.testcodewitharchitecture.user.service;

import com.example.testcodewitharchitecture.common.domain.exception.CertificationCodeNotMatchedException;
import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import com.example.testcodewitharchitecture.user.infrastructure.UserEntity;
import com.example.testcodewitharchitecture.user.service.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final CertificationService certificationService;

    public UserEntity getByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE).orElseThrow(() -> new ResourceNotFoundException("Users", email));
    }

    public UserEntity getById(long id) {
        return userRepository.findByIdAndStatus(id, UserStatus.ACTIVE).orElseThrow(() -> new ResourceNotFoundException("Users", id));
    }

    @Transactional
    public UserEntity create(UserCreate userCreate) {
        UserEntity userEntity = new UserEntity();
        userEntity.setEmail(userCreate.getEmail());
        userEntity.setNickname(userCreate.getNickname());
        userEntity.setAddress(userCreate.getAddress());
        userEntity.setStatus(UserStatus.PENDING);
        userEntity.setCertificationCode(UUID.randomUUID().toString());
        userEntity = userRepository.save(userEntity);
        certificationService.send(userCreate.getEmail(), userEntity.getId(), userEntity.getCertificationCode());
        return userEntity;
    }

    @Transactional
    public UserEntity update(long id, UserUpdate userUpdate) {
        UserEntity userEntity = getById(id);
        userEntity.setNickname(userUpdate.getNickname());
        userEntity.setAddress(userUpdate.getAddress());
        userEntity = userRepository.save(userEntity);
        return userEntity;
    }

    @Transactional
    public void login(long id) {
        UserEntity userEntity = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Users", id));
        userEntity.setLastLoginAt(Clock.systemUTC().millis());
    }

    @Transactional
    public void verifyEmail(long id, String certificationCode) {
        UserEntity userEntity = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Users", id));
        if (!certificationCode.equals(userEntity.getCertificationCode())) {
            throw new CertificationCodeNotMatchedException();
        }
        userEntity.setStatus(UserStatus.ACTIVE);
    }
}
