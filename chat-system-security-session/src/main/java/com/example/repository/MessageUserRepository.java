package com.example.repository;

import com.example.entity.MessageUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageUserRepository extends JpaRepository<MessageUserEntity, Long> {

    Optional<MessageUserEntity> findByUsername(@NonNull String username);
}
