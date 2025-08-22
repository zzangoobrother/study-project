package com.example.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.domain.Message;

public interface MessageRepository extends JpaRepository<Message, Long> {
}
