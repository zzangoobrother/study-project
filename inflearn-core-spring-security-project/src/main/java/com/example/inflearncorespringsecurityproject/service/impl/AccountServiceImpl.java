package com.example.inflearncorespringsecurityproject.service.impl;

import com.example.inflearncorespringsecurityproject.domain.Account;
import com.example.inflearncorespringsecurityproject.repository.UserRepository;
import com.example.inflearncorespringsecurityproject.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;

    public AccountServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @Override
    public void createUser(Account account) {
        userRepository.save(account);
    }
}
