package com.example.inflearncorespringsecurityproject.service;

import com.example.inflearncorespringsecurityproject.domain.dto.AccountDto;
import com.example.inflearncorespringsecurityproject.domain.entity.Account;

import java.util.List;

public interface UserService {

    void createUser(Account account);

    void modifyUser(AccountDto accountDto);

    List<Account> getUsers();

    AccountDto getUser(Long id);

    void deleteUser(Long idx);

    void order();
}
