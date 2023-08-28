package com.example.testcodewitharchitecture.user.service;

import com.example.testcodewitharchitecture.common.domain.exception.ResourceNotFoundException;
import com.example.testcodewitharchitecture.common.service.port.ClockHolder;
import com.example.testcodewitharchitecture.common.service.port.UuidHolder;
import com.example.testcodewitharchitecture.user.controller.port.*;
import com.example.testcodewitharchitecture.user.domain.User;
import com.example.testcodewitharchitecture.user.domain.UserCreate;
import com.example.testcodewitharchitecture.user.domain.UserStatus;
import com.example.testcodewitharchitecture.user.domain.UserUpdate;
import com.example.testcodewitharchitecture.user.service.port.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserCreateService, UserUpdateService, UserReadService, AuthenticationService {

    private final UserRepository userRepository;
    private final CertificationService certificationService;
    private final UuidHolder uuidHolder;
    private final ClockHolder clockHolder;

    @Override
    public User getByEmail(String email) {
        return userRepository.findByEmailAndStatus(email, UserStatus.ACTIVE).orElseThrow(() -> new ResourceNotFoundException("Users", email));
    }

    @Override
    public User getById(long id) {
        return userRepository.findByIdAndStatus(id, UserStatus.ACTIVE).orElseThrow(() -> new ResourceNotFoundException("Users", id));
    }

    @Transactional
    @Override
    public User create(UserCreate userCreate) {
        User user = User.from(userCreate, uuidHolder);
        user = userRepository.save(user);
        certificationService.send(userCreate.getEmail(), user.getId(), user.getCertificationCode());
        return user;
    }

    @Transactional
    @Override
    public User update(long id, UserUpdate userUpdate) {
        User user = getById(id);
        user = user.update(userUpdate);
        user = userRepository.save(user);
        return user;
    }

    @Transactional
    @Override
    public void login(long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Users", id));
        user = user.login(clockHolder);
        userRepository.save(user);
    }

    @Transactional
    @Override
    public void verifyEmail(long id, String certificationCode) {
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Users", id));
        user = user.certificate(certificationCode);
        userRepository.save(user);
    }
}
