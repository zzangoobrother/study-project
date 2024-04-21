package com.example.inflearncorespringsecurityproject.repository;

import com.example.inflearncorespringsecurityproject.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Account, Long> {

    Account findByUsername(String username);
}
