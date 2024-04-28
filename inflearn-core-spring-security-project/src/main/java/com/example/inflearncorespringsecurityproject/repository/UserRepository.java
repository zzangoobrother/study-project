package com.example.inflearncorespringsecurityproject.repository;

import com.example.inflearncorespringsecurityproject.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Account, Long> {

    Account findByUsername(String username);

    int countByUsername(String username);
}
